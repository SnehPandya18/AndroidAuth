package com.snehpandya.androidauth;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

/**
 * Created by sneh.pandya on 09/11/17.
 */

@RequiresApi(api = Build.VERSION_CODES.M)
public class FingerprintHandler extends FingerprintManager.AuthenticationCallback {

    //TODO: 09. Implement CancellationSignal
    /*
        CancellationSignal is used whenever your app can no longer process user input,
        e.g. app in background. If you don't implement this, other apps won't be able
        to access touch sensor, including lock screen.
    */

    private CancellationSignal mCancellationSignal;
    private Context mContext;

    public FingerprintHandler(Context context) {
        mContext = context;
    }

    //TODO: 10. Implement startAuth() method, responsible for starting the authentication
    public void startAuth(FingerprintManager fingerprintManager, FingerprintManager.CryptoObject cryptoObject) {
        mCancellationSignal = new CancellationSignal();
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fingerprintManager.authenticate(cryptoObject, mCancellationSignal, 0, this, null);
    }


    //TODO: 11. Implement onAuthenticationError
    @Override
    public void onAuthenticationError(int errorCode, CharSequence errString) {
        Toast.makeText(mContext, "Authentication Error!", Toast.LENGTH_SHORT).show();
    }

    //TODO: 12. Implement onAuthenticationFailed
    @Override
    public void onAuthenticationFailed() {
        Toast.makeText(mContext, "Authentication Failed!", Toast.LENGTH_SHORT).show();
    }

    //TODO: 13. Implement onAuthenticationHelp
    //Used for non-fatal errors
    @Override
    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        Toast.makeText(mContext, "Authentication Help: " + helpString, Toast.LENGTH_SHORT).show();
    }

    //TODO: 14. Implement onAuthenticationSucceeded
    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        Toast.makeText(mContext, "Success!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(mContext, ContentActivity.class);
        mContext.startActivity(intent);
    }
}
