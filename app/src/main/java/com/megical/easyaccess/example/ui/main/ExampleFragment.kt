package com.megical.easyaccess.example.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.WriterException
import com.megical.easyaccess.example.SettingsRepository
import com.megical.easyaccess.example.databinding.MainFragmentBinding
import com.megical.easyaccess.sdk.LoginData
import com.megical.easyaccess.sdk.LoginState
import timber.log.Timber


const val LOGIN_ACTIVITY = 1

class ExampleFragment : Fragment() {

    private var _binding: MainFragmentBinding? = null
    private val binding get() = _binding!!

    private val prefRepository by lazy { SettingsRepository(requireContext()) }

    companion object {
        fun newInstance() = ExampleFragment()
    }

    private lateinit var viewModel: ExampleViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = MainFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProvider(this).get(ExampleViewModel::class.java)

        viewModel.getHealthcheck().observe(this, {
            binding.healthcheckTextView.text = if (it != null) {
                "Playground running: ${it.buildDate}"
            } else {
                "Playground DOWN!"
            }
        })

        // Get Registered client data
        prefRepository.getClientData()
            .let {
                if (it != null) {
                    viewModel.setClientData(it)
                } else {
                    // Handle app link to register client
                    requireActivity().intent?.data?.getQueryParameter("clientToken")
                        ?.let { clientToken ->
                            viewModel.createClient(clientToken)
                        }
                }
            }

        // Set client data to viewModel
        viewModel.getClientData().observe(this, {
            prefRepository.setClientData(it)
        })

        // Show different pages
        viewModel.getViewState().observe(this, {
            binding.register.visibility = View.GONE
            binding.authenticate.visibility = View.GONE
            binding.loggedIn.visibility = View.GONE
            binding.loading.visibility = View.GONE
            binding.easyAccess.visibility = View.GONE
            when (it) {
                null, ViewState.RegisterClient -> {
                    binding.register.visibility = View.VISIBLE
                }
                ViewState.Loading -> {
                    binding.loading.visibility = View.VISIBLE
                }
                ViewState.Authenticate -> {
                    binding.authenticate.visibility = View.VISIBLE
                }
                ViewState.LoggedIn -> {
                    binding.loggedIn.visibility = View.VISIBLE
                }
                ViewState.EasyAccess -> {
                    binding.easyAccess.visibility = View.VISIBLE
                }
            }
        })

        binding.registerButton.setOnClickListener {
            binding.registerTokenInput.text?.toString()
                ?.let {
                    viewModel.createClient(it)
                }
        }

        binding.logoutButton.setOnClickListener {
            viewModel.logout()
        }

        viewModel.getTokenSet().observe(this, { tokenSet ->
            binding.subjectMessage.text = tokenSet.sub

            // Fetch message from playground with access token
            viewModel.fetchMessageFromTestService(tokenSet.accessToken)
                .observe(this, { helloResponse ->
                    helloResponse?.let {
                        binding.backendMessage.text = "Hello: ${it.hello}"
                    }
                })
        })

        // Easy Access specific handlers
        binding.deregisterButton.setOnClickListener { viewModel.deregisterClient() }
        binding.loginButton.setOnClickListener { viewModel.authenticate() }
        viewModel.getAuthentication().observe(this, ::handleLoginData)
        viewModel.getLoginState().observe(this, ::handleLoginState)
        viewModel.getMetadata().observe(this, { metadata ->
            binding.metadataMessage.text = metadata
                .values
                .joinToString("\n") { value ->
                    value.translations.first { it.lang == metadata.defaultLang }.value
                }
        })
    }

    private fun handleLoginState(loginState: LoginState) {
        binding.stateMessage.text = loginState.value
        when (loginState) {
            LoginState.Init,
            LoginState.Started,
            -> {
                Thread {
                    Handler(Looper.getMainLooper())
                        .postDelayed(viewModel::fetchLoginState, 5000)
                }.start()
            }
            LoginState.Updated -> {
                viewModel.verifyLoginData()
            }
            else -> {
                Timber.e("Unhandled state")
            }
        }
    }

    private fun handleLoginData(loginData: LoginData) {
        val intent = Intent(Intent.ACTION_VIEW, loginData.appLink)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(intent, LOGIN_ACTIVITY)
        } else {
            viewModel.fetchMetadata()
            viewModel.fetchLoginState()
            binding.loginCodeMessage.text = loginData.loginCode

            try {
                val qrgEncoder =
                    QRGEncoder(loginData.appLink.toString(), null, QRGContents.Type.TEXT, 200)
                binding.qrCode.setImageBitmap(qrgEncoder.bitmap)
            } catch (e: WriterException) {
                Timber.e(e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_ACTIVITY) {
            viewModel.verifyLoginData()
        } else {
            Timber.e("Invalid requestCode: $requestCode")
        }
    }
}