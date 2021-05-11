package com.megical.easyaccess.example.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
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
import com.megical.easyaccess.sdk.State
import kotlinx.android.synthetic.main.main_fragment.*
import timber.log.Timber


const val LOGIN_ACTIVITY = 1

class ExampleFragment : Fragment() {

    private val prefRepository by lazy { SettingsRepository(requireContext()) }

    companion object {
        fun newInstance() = ExampleFragment()
    }

    private lateinit var exampleViewModel: ExampleViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        exampleViewModel = ViewModelProvider(this).get(ExampleViewModel::class.java)

        exampleViewModel.getHealthcheck().observe(this, {
            healthcheckTextView.text = "Playground running"
        })

        prefRepository.getClientData()?.let {
            exampleViewModel.setClientData(it)
        }

        exampleViewModel.getClientData().observe(this, {
            prefRepository.setClientData(it)
        })

        loading.visibility = View.VISIBLE
        exampleViewModel.getViewState().observe(this, {
            register.visibility = View.GONE
            authenticate.visibility = View.GONE
            waitLoginCode.visibility = View.GONE
            hello.visibility = View.GONE
            loading.visibility = View.GONE
            easyaccess.visibility = View.GONE
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
                ViewState.WaitLoginData -> {
                    waitLoginCode.visibility = View.VISIBLE
                }
                ViewState.Hello -> {
                    hello.visibility = View.VISIBLE
                }
                ViewState.Easyaccess -> {
                    easyaccess.visibility = View.VISIBLE
                }
            }
        })

        registerButton.setOnClickListener {

            registerTokenInput.text?.toString()
                ?.let {
                    exampleViewModel.registerClient(it)
                }
        }

        logoutButton.setOnClickListener {
            exampleViewModel.logout()
        }

        // Easy Access
        deregisterButton.setOnClickListener {
            exampleViewModel.deregisterClient()
        }

        loginButton.setOnClickListener {
            exampleViewModel.authenticate()
        }

        exampleViewModel.getTokenSet().observe(this, { tokenSet ->
            subjectMessage.text = tokenSet.sub

            // Fetch message from playground with access token
            exampleViewModel.hello(tokenSet.accessToken).observe(this, { helloResponse ->
                helloResponse?.let {
                    backendMessage.text = "Hello: ${it.hello}"
                }
            })
        })

        exampleViewModel.getAuthentication().observe(this, {
            val intent = Intent(Intent.ACTION_VIEW, it.appLink)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivityForResult(intent, LOGIN_ACTIVITY)
            } else {
                exampleViewModel.fetchMetadata()
                exampleViewModel.fetchState()
                loginCodeMessage.text = it.loginCode

                val qrgEncoder =
                    QRGEncoder("${it.appLink}", null, QRGContents.Type.TEXT, 200)
                try {
                    qrCode.setImageBitmap(qrgEncoder.bitmap)
                } catch (e: WriterException) {
                    Timber.e(e)
                }
            }
        })

        exampleViewModel.getState().observe(this, {
            stateMessage.text = it.value
            when (it) {
                State.Updated -> {
                    exampleViewModel.verify()
                }
                State.Init,
                State.Started,
                -> {
                    Handler().postDelayed({
                        exampleViewModel.fetchState()
                    }, 5000)
                }
                else -> {
                    Timber.e("Unhandled state")
                }
            }
        })

        exampleViewModel.getMetadata().observe(this, { metadata ->
            metadataMessage.text = metadata
                .values
                .joinToString("\n") { value ->
                    val t = value.translations.first { it.lang == metadata.defaultLang }
                    t.value
                }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_ACTIVITY) {
            exampleViewModel.verify()
        }
    }
}