package com.snehpandya.androidauth;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.annotation.RequiresApi;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialPickerConfig;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.HintRequest;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * Created by sneh.pandya on 09/11/17.
 */

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // TODO: 01. Add Fingerprint Feature + Permission in AndroidManifest.xml
    // TODO: 02. Add Google Play Services Auth dependency in app level `build.gradle`

    /*
        * Fingerprint Authentication API: https://www.androidauthority.com/how-to-add-fingerprint-authentication-to-your-android-app-747304/

        * Google Smart Lock: https://www.androidauthority.com/how-to-integrate-smart-lock-in-android-apps-702638/
        |
        -> https://developers.google.com/identity/smartlock-passwords/android/
    */

    public static final String KEY_NAME = "fingerprintKey";
    private static final String TAG = "LoginActivity";
    private static final int RC_SAVE = 1;
    private static final int RC_HINT = 2;
    private static final int RC_READ = 3;
    private static final String IS_RESOLVING = "is_resolving";
    private static final String IS_REQUESTING = "is_requesting";
    private boolean mIsResolving;
    private boolean mIsRequesting;

    private TextView mTextView;
    private Button mSignInButton;
    private ProgressBar mSignInProgressBar;
    private TextInputLayout mUsernameTextInputLayout;
    private EditText mUsernameEditText;
    private TextInputLayout mPasswordTextInputLayout;
    private EditText mPasswordEditText;

    private Cipher mCipher;
    private KeyStore mKeyStore;
    private KeyGenerator mKeyGenerator;

    private GoogleApiClient mGoogleApiClient;

    private KeyguardManager mKeyguardManager;
    private FingerprintManager mFingerprintManager;
    private FingerprintManager.CryptoObject mCryptoObject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text_description);

        executeFingerprint();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .enableAutoManage(this, 0, this)
                .addApi(Auth.CREDENTIALS_API)
                .build();

        if (savedInstanceState != null) {
            mIsResolving = savedInstanceState.getBoolean(IS_RESOLVING);
            mIsRequesting = savedInstanceState.getBoolean(IS_REQUESTING);
        }

        mUsernameTextInputLayout = (TextInputLayout) findViewById(R.id.usernameTextInputLayout);
        mPasswordTextInputLayout = (TextInputLayout) findViewById(R.id.passwordTextInputLayout);

        mUsernameEditText = (EditText) findViewById(R.id.usernameEditText);
        mPasswordEditText = (EditText) findViewById(R.id.passwordEditText);

        mSignInButton = (Button) findViewById(R.id.signInButton);
        mSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                setSignInEnabled(false);
                String username = mUsernameTextInputLayout.getEditText().getText().toString();
                String password = mPasswordTextInputLayout.getEditText().getText().toString();

                if (!TextUtils.isEmpty(mUsernameEditText.getText()) && !TextUtils.isEmpty(mUsernameEditText.getText())) {
                    Credential credential = new Credential.Builder(username)
                            .setPassword(password)
                            .build();
                    if (Util.isValidCredential(credential)) {
                        saveCredential(credential);
                    }
                } else {
                    Log.d(TAG, "Credentials are invalid. Username or password are incorrect.");
                    Toast.makeText(MainActivity.this, R.string.invalid_creds_toast_msg,
                            Toast.LENGTH_SHORT).show();
                    setSignInEnabled(true);
                }
            }
        });

        mSignInProgressBar = (ProgressBar) findViewById(R.id.signInProgress);
        mSignInProgressBar.setVisibility(ProgressBar.INVISIBLE);
    }

    private void executeFingerprint() {

        //TODO: 03. Check minSdk
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            //Get instance of Keyguard Manager
            mKeyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

            //Get instace of Fingerprint Manager
            mFingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

            if (!mFingerprintManager.isHardwareDetected()) {
                mTextView.setText(R.string.description_fingerprint_unavailable);
            } else {

                //TODO: 04. Check whether the device has fingerprint sensor
                //Check whether the device has fingerprint sensor
                if (!mFingerprintManager.isHardwareDetected()) {
                    mTextView.setText(R.string.description_unsupported);
                }

                //TODO: 05. Check whether user has granted "USE_FINGERPRINT" permission
                //Check whether user has granted "USE_FINGERPRINT" permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                    mTextView.setText(R.string.description_enable_fingerprint);
                }

                //TODO: 06. Check that the user has registered at least one fingerprint
                //Check that the user has registered at least one fingerprint
                if (!mFingerprintManager.hasEnrolledFingerprints()) {
                    mTextView.setText(R.string.description_register_fingerprint);
                }

                //TODO: 07. Check whether the lock screen is secured
                //Check whether the lock screen is secured
                if (!mKeyguardManager.isKeyguardSecure()) {
                    mTextView.setText(R.string.description_enable_lockscreen);
                } else {
                    try {
                        generateKey();
                    } catch (FingerprintException exception) {
                        exception.printStackTrace();
                    }

                    //TODO: 08. Create CryptoObject & start authentication
                    if (initCipher()) {
                        //If the Cipher is initialized successfully, then create a CryptoObject instance
                        mCryptoObject = new FingerprintManager.CryptoObject(mCipher);

                        //FingerprintHandler to start authentication
                        FingerprintHandler mFinerprintHandler = new FingerprintHandler(this);
                        mFinerprintHandler.startAuth(mFingerprintManager, mCryptoObject);
                    }
                }
            }
        }
    }

    private void callHintRequest() {
        HintRequest hintRequest = new HintRequest.Builder()
                .setHintPickerConfig(new CredentialPickerConfig.Builder()
                        .setShowCancelButton(true)
                        .build())
                .setEmailAddressIdentifierSupported(true)
                .setAccountTypes(IdentityProviders.GOOGLE,
                        IdentityProviders.MICROSOFT,
                        IdentityProviders.LINKEDIN,
                        IdentityProviders.FACEBOOK,
                        IdentityProviders.TWITTER)
                .build();

        PendingIntent pendingIntent = Auth.CredentialsApi.getHintPickerIntent(mGoogleApiClient, hintRequest);

        try {
            startIntentSenderForResult(pendingIntent.getIntentSender(), RC_HINT, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "callHintRequest: Could not start hint picker intent", e);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(IS_RESOLVING, mIsResolving);
        savedInstanceState.putBoolean(IS_REQUESTING, mIsRequesting);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult:" + requestCode + ":" + resultCode + ":" + data);

        if (requestCode == RC_READ) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                processRetrievedCredential(credential);
            } else {
                Log.e(TAG, "Credential Read: NOT OK");
                setSignInEnabled(true);
            }
        } else if (requestCode == RC_HINT) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                goToContent();
            } else {
                Log.d(TAG, "onActivityResult: Hint read: NOT OK!");
            }
        } else if (requestCode == RC_SAVE) {
            Log.d(TAG, "Result code: " + resultCode);
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Credential Save: OK");
            } else {
                Log.e(TAG, "Credential Save Failed");
            }
            goToContent();
        }
        mIsResolving = false;
    }

    private void requestCredentials() {
        mSignInButton.setEnabled(false);
        mIsRequesting = true;

        CredentialRequest request = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true)
                .setAccountTypes(IdentityProviders.GOOGLE, IdentityProviders.TWITTER)
                .build();

        Auth.CredentialsApi.request(mGoogleApiClient, request).setResultCallback(
                new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(CredentialRequestResult credentialRequestResult) {

                        Status status = credentialRequestResult.getStatus();
                        if (credentialRequestResult.getStatus().isSuccess()) {
                            /* Successfully read the credential without any user interaction,
                            only a single credential and the user has auto sign-in enabled. */
                            Credential credential = credentialRequestResult.getCredential();
                            processRetrievedCredential(credential);
                        } else if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED) {
                            /* This is most likely the case where the user has multiple saved
                            credentials and needs to pick one. */
                            resolveResult(status, RC_READ);
                        } else if (status.getStatusCode() == CommonStatusCodes.SIGN_IN_REQUIRED) {
                            /* This is most likely the case where the user does not currently
                             have any saved credentials and thus needs to provide a username
                             and password to sign in.*/
                            callHintRequest();
                            Log.d(TAG, "Sign in required");
                            mSignInButton.setEnabled(true);
                        } else {
                            Log.w(TAG, "Unrecognized status code: " + status.getStatusCode());
                            mSignInButton.setEnabled(true);
                        }
                    }
                }
        );
    }

    private void processRetrievedCredential(Credential credential) {
        String accountType = credential.getAccountType();
        if (accountType == null) {
            if (Util.isValidCredential(credential)) {
                goToContent();
            } else {
                Log.d(TAG, "Retrieved credential invalid, so delete retrieved" + " credential.");
                Toast.makeText(this, "Retrieved credentials are invalid, so will be deleted.", Toast.LENGTH_LONG).show();
                deleteCredential(credential);
                requestCredentials();
                mSignInButton.setEnabled(false);
            }
        } else if (accountType.equals(IdentityProviders.GOOGLE)) {
            GoogleSignInOptions gso =
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .build();
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .enableAutoManage(this, this)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                    .setAccountName(credential.getId())
                    .build();
            OptionalPendingResult<GoogleSignInResult> opr =
                    Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        }
    }

    private void resolveResult(Status status, int requestCode) {
        /* We don't want to fire multiple resolutions at once since that can result
         in stacked dialogs after rotation or another similar event.*/
        if (mIsResolving) {
            Log.w(TAG, "resolveResult: already resolving.");
            return;
        }

        Log.d(TAG, "Resolving: " + status);
        if (status.hasResolution()) {
            Log.d(TAG, "STATUS: RESOLVING");
            try {
                status.startResolutionForResult(this, requestCode);
                mIsResolving = true;
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "STATUS: Failed to send resolution.", e);
            }
        } else {
            goToContent();
        }
    }

    protected void saveCredential(Credential credential) {
        Auth.CredentialsApi.save(mGoogleApiClient,
                credential).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Log.d(TAG, "Credential saved");
                    goToContent();
                } else {
                    Log.d(TAG, "Attempt to save credential failed " +
                            status.getStatusMessage() + " " +
                            status.getStatusCode());
                    resolveResult(status, RC_SAVE);
                }
            }
        });
    }

    private void deleteCredential(Credential credential) {
        Auth.CredentialsApi.delete(mGoogleApiClient,
                credential).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Log.d(TAG, "Credential successfully deleted.");
                } else {
                    Log.d(TAG, "Credential not deleted successfully.");
                }
            }
        });
    }

    protected void setSignInEnabled(boolean enable) {
        mSignInButton.setEnabled(enable);
        mUsernameEditText.setEnabled(enable);
        mPasswordEditText.setEnabled(enable);
        if (!enable) {
            mSignInProgressBar.setVisibility(ProgressBar.VISIBLE);
        } else {
            mSignInProgressBar.setVisibility(ProgressBar.INVISIBLE);
        }
    }

    private void goToContent() {
        startActivity(new Intent(this, ContentActivity.class));
        finish();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        requestCredentials();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended: " + cause);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: " + connectionResult);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void generateKey() throws FingerprintException {
        try {
            //Obtain a reference to the KeyStore
            mKeyStore = KeyStore.getInstance("AndroidKeyStore");

            //Generate the key
            mKeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            //Initialize empty KeyStore
            mKeyStore.load(null);

            //Initialize KeyGenerator
            mKeyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());

            //Generate the key
            mKeyGenerator.generateKey();
        } catch (KeyStoreException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidAlgorithmParameterException
                | CertificateException
                | IOException e) {
            e.printStackTrace();
            throw new FingerprintException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean initCipher() {
        try {
            //Obtain a Cipher instance & configure it for fingerprint authentication
            mCipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException e) {
            throw new RuntimeException("Failed to get Cipher", e);
        }

        try {
            mKeyStore.load(null);
            SecretKey secretKey = (SecretKey) mKeyStore.getKey(KEY_NAME, null);
            mCipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            return false;
        } catch (KeyStoreException
                | CertificateException
                | IOException
                | NoSuchAlgorithmException
                | InvalidKeyException
                | UnrecoverableKeyException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    private class FingerprintException extends Exception {
        FingerprintException(Exception e) {
            super(e);
        }
    }
}

