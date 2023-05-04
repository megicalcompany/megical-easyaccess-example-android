package com.megical.easyaccess.example.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.WriterException
import com.megical.easyaccess.example.SettingsRepository
import com.megical.easyaccess.example.databinding.MainFragmentBinding
import com.megical.easyaccess.sdk.LoginData
import com.megical.easyaccess.sdk.LoginState
import com.megical.easyaccess.sdk.SigningData
import timber.log.Timber
import java.security.MessageDigest

class ExampleFragment : Fragment() {

    companion object {
        const val REQUEST_CODE_AUTHENTICATE = 2098
        const val REQUEST_CODE_SIGNATURE = 2099

        fun newInstance() = ExampleFragment()
    }
    
    private var _binding: MainFragmentBinding? = null
    private val binding get() = _binding!!

    private val prefRepository by lazy { SettingsRepository(requireContext()) }

    private lateinit var viewModel: ExampleViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = MainFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ExampleViewModel::class.java]

        viewModel.getHealthcheck().observe(viewLifecycleOwner) {
            binding.healthcheckTextView.text = if (it != null) {
                "Playground running: ${it.buildDate}"
            } else {
                "Playground DOWN!"
            }
        }

        // Get Registered client data or Handle app link to register client
        prefRepository.clientData?.let { viewModel.setClientData(it) }
            ?: requireActivity().intent?.data?.getQueryParameter("clientToken")?.let {
                viewModel.createClient(it)
            }

        // Save registered client data to shared prefs
        viewModel.getClientData().observe(viewLifecycleOwner) {
            prefRepository.clientData = it
        }

        // Show different pages
        viewModel.getViewState().observe(viewLifecycleOwner) {
            binding.register.visibility = View.GONE
            binding.authenticate.visibility = View.GONE
            binding.authResult.visibility = View.GONE
            binding.loading.visibility = View.GONE
            binding.easyAccess.visibility = View.GONE
            binding.signResult.visibility = View.GONE
            when (it) {
                null, ViewState.RegisterClient -> {
                    binding.register.visibility = View.VISIBLE
                }
                ViewState.Loading -> {
                    binding.loading.visibility = View.VISIBLE
                }
                ViewState.Operate -> {
                    binding.authenticate.visibility = View.VISIBLE
                }
                ViewState.AuthResult -> {
                    binding.authResult.visibility = View.VISIBLE
                }
                ViewState.EasyAccess -> {
                    binding.easyAccess.visibility = View.VISIBLE
                }
                ViewState.SignResult -> {
                    binding.signResult.visibility = View.VISIBLE
                }
            }
        }

        binding.registerButton.setOnClickListener {
            binding.registerTokenInput.text?.toString()
                ?.let {
                    viewModel.createClient(it)
                }
        }

        binding.logoutButton.setOnClickListener {
            viewModel.clearOperation()
        }

        viewModel.getTokenSet().observe(viewLifecycleOwner) { tokenSet ->
            binding.subjectMessage.text = tokenSet.sub

            // Fetch message from playground with access token
            viewModel.fetchMessageFromTestService(tokenSet.accessToken)
                .observe(viewLifecycleOwner) { helloResponse ->
                    helloResponse?.let {
                        binding.backendMessage.text = "Hello: ${it.hello}"
                    }
                }
        }
        
        viewModel.getDataToSign().observe(viewLifecycleOwner) {
            binding.signResultData.text = it
        }

        // Easy Access specific handlers
        binding.deregisterButton.setOnClickListener { viewModel.deregisterClient() }
        binding.loginButton.setOnClickListener { viewModel.authenticate() }
        binding.signButton.setOnClickListener { 
            
            // Example data is just hashed timestamp
            val exampleData = MessageDigest
                .getInstance("SHA-512")
                .digest(System.currentTimeMillis().toString().toByteArray())
                .let {
                    Base64.encode(it, Base64.URL_SAFE or Base64.NO_WRAP).decodeToString()
                }
            
            viewModel.sign(exampleData)
        }
        
        viewModel.getAuthentication().observe(viewLifecycleOwner, ::handleLoginData)
        viewModel.getLoginState().observe(viewLifecycleOwner, ::handleLoginState)
        viewModel.getMetadata().observe(viewLifecycleOwner) { metadata ->
            binding.metadataMessage.text = metadata
                .values
                .joinToString("\n") { value ->
                    value.translations.first { it.lang == metadata.defaultLang }.value
                }
        }
        
        viewModel.getSigningResult().observe(viewLifecycleOwner, ::handleSigningResult)
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
            startActivityForResult(intent, REQUEST_CODE_AUTHENTICATE)
        } else {
            viewModel.fetchMetadata()
            viewModel.fetchLoginState()
            binding.loginCodeTitle.text = "Login code:"
            binding.loginCodeMessage.text = loginData.loginCode
            trySetQR(loginData.appLink.toString())
        }
    }

    private fun handleSigningResult(signingData: SigningData) {
        val intent = Intent(Intent.ACTION_VIEW, signingData.appLink)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(intent, REQUEST_CODE_SIGNATURE)
        } else {
            viewModel.showEasyAccessRequest()
            binding.loginCodeTitle.text = "Signature code:"
            binding.loginCodeMessage.text = signingData.signatureCode
            trySetQR(signingData.appLink.toString())
        }
    }

    private fun trySetQR(data: String) {
        try {
            val qrgEncoder = QRGEncoder(data, null, QRGContents.Type.TEXT, 200)
            binding.qrCode.setImageBitmap(qrgEncoder.bitmap)
        } catch (e: WriterException) {
            Timber.e(e)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_AUTHENTICATE) {
            if(resultCode == Activity.RESULT_OK) {
                viewModel.verifyLoginData()
            } else {
                viewModel.clearOperation()
                Toast.makeText(requireContext(), "User cancelled authentication", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_CODE_SIGNATURE) {
            if(resultCode == Activity.RESULT_OK) {
                viewModel.showSigningResult()
                binding.signResultResult.text = "OK"
            } else {
                viewModel.clearOperation()
                Toast.makeText(requireContext(), "User cancelled signing", Toast.LENGTH_SHORT).show()
            }
        }
    }
}