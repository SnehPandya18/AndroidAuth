package com.snehpandya.androidauth;

import com.google.android.gms.auth.api.credentials.Credential;

/**
 * Created by sneh.pandya on 09/11/17.
 */

public class Util {

    private static String[][] validCredentials = {
            {"user1", "password1"},
            {"user2", "password2"}
    };

    public static boolean isValidCredential(String username, String password) {
        for (String[] credential :
                validCredentials) {
            if (credential[0].equals(username) && credential[1].equals(password))
                return true;
        }
        return false;
    }

    public static boolean isValidCredential(Credential credential) {
        String username = credential.getId();
        String password = credential.getPassword();
        return isValidCredential(username, password);
    }
}
