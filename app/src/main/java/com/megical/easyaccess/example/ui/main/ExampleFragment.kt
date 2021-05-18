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
import com.megical.easyaccess.example.R
import com.megical.easyaccess.example.SettingsRepository
import com.megical.easyaccess.sdk.LoginData
import com.megical.easyaccess.sdk.LoginState
import kotlinx.android.synthetic.main.main_fragment.*
import timber.log.Timber


const val LOGIN_ACTIVITY = 1

class ExampleFragment : Fragment() {

    private val prefRepository by lazy { SettingsRepository(requireContext()) }

    companion object {
        fun newInstance() = ExampleFragment()
    }

    private lateinit var viewModel: ExampleViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProvider(this).get(ExampleViewModel::class.java)

        requireActivity().intent?.data?.getQueryParameter("clientToken")?.let {
            viewModel.createClient(it)
        }

        viewModel.getHealthcheck().observe(this, {
            healthcheckTextView.text = if (it != null) {
                "Playground running: ${it.buildDate}"
            } else {
                "Playground DOWN!"
            }
        })

        prefRepository.getClientData()?.let {
            viewModel.setClientData(it)
        }

        viewModel.getClientData().observe(this, {
            prefRepository.setClientData(it)
        })
        viewModel.getViewState().observe(this, {
            register.visibility = View.GONE
            authenticate.visibility = View.GONE
            loggedIn.visibility = View.GONE
            loading.visibility = View.GONE
            easyAccess.visibility = View.GONE
            when (it) {
                null, ViewState.RegisterClient -> {
                    register.visibility = View.VISIBLE
                }
                ViewState.Loading -> {
                    loading.visibility = View.VISIBLE
                }
                ViewState.Authenticate -> {
                    authenticate.visibility = View.VISIBLE
                }
                ViewState.LoggedIn -> {
                    loggedIn.visibility = View.VISIBLE
                }
                ViewState.EasyAccess -> {
                    easyAccess.visibility = View.VISIBLE
                }
            }
        })

        registerButton.setOnClickListener {
            registerTokenInput.text?.toString()
                ?.let {
                    viewModel.createClient(it)
                }
        }

        logoutButton.setOnClickListener {
            viewModel.logout()
        }

        viewModel.getTokenSet().observe(this, { tokenSet ->
            subjectMessage.text = tokenSet.sub

            // Fetch message from playground with access token
            viewModel.fetchMessageFromTestService(tokenSet.accessToken)
                .observe(this, { helloResponse ->
                    helloResponse?.let {
                        backendMessage.text = "Hello: ${it.hello}"
                    }
                })
        })

        // Easy Access
        deregisterButton.setOnClickListener { viewModel.deregisterClient() }
        loginButton.setOnClickListener { viewModel.authenticate() }
        viewModel.getAuthentication().observe(this, ::handleLoginData)
        viewModel.getLoginState().observe(this, ::handleLoginState)
        viewModel.getMetadata().observe(this, { metadata ->
            metadataMessage.text = metadata
                .values
                .joinToString("\n") { value ->
                    value.translations.first { it.lang == metadata.defaultLang }.value
                }
        })
    }

    private fun handleLoginState(loginState: LoginState) {
        stateMessage.text = loginState.value
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
            loginCodeMessage.text = loginData.loginCode

            try {
                val qrgEncoder =
                    QRGEncoder(loginData.appLink.toString(), null, QRGContents.Type.TEXT, 200)
                qrCode.setImageBitmap(qrgEncoder.bitmap)
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