package net.skdr.security;

import danger.app.Application;
import danger.app.AppResources;
import danger.app.DataStore;
import danger.app.ComponentVersion;

import danger.audio.AudioManager;
import danger.audio.Meta;

import danger.crypto.MD5;

import danger.net.HiptopConnection;

import danger.ui.Bitmap;
import danger.ui.MarqueeAlert;
import danger.ui.NotificationManager;

import danger.util.DEBUG;
import danger.util.StringUtils;
import danger.util.ByteArray;

public class Security implements Resources
{
    private static final String APPLICATION_NAME = Application.getCurrentApp().getString(AppResources.ID_APP_NAME);
    private static final String DATASTORE_NAME = APPLICATION_NAME + "." + "persist";

    private static final ComponentVersion versionInfo = new ComponentVersion(Application.getCurrentApp().getBundle().getName());
    private static DataStore securityDataStore;
    private static Bitmap marqueeIcon;
    static
    {
        Application.getCurrentApp().getBundle().getComponentVersion(versionInfo);
        securityDataStore = DataStore.createDataStore(DATASTORE_NAME, true, false);
        marqueeIcon = Application.getCurrentApp().getBitmap(AppResources.ID_SMALL_ICON);
    }

    // TODO:  need a version of this method that defaults showError=true
    public static boolean isValidCopy(String secretKey, boolean showError)
    {
        if(hasValidUser(secretKey, showError) &&
           hasNotExpired(secretKey, showError) &&
           hasNotReachedMaxUses(secretKey, showError))
        {
            // all is well
            return true;
        }
        // all is not well
        return false;
    }

    public static boolean hasValidUser(String secretKey, boolean showError)
    {
        String user = HiptopConnection.getUserName();
        if (user == null)
        {
            user = "you";
        }

        String errorMsg = "This version of '" + APPLICATION_NAME + "' is not registered to " + user + ".";

        // do the hash
        String calcUserHash = hash(user, secretKey);

        // get the hashed username from the resources and see if they match
        String rsrcUserHash = Application.getCurrentApp().getString(kStrUserHash).toLowerCase();
        if (calcUserHash.compareTo(rsrcUserHash) == 0)
        {
            return true;
        }

        // failed
        if(showError)
        {
            showError(errorMsg);
        }
        return false;
    }

    public static boolean hasNotExpired(String secretKey, boolean showError)
    {
        String errorMsg = "This time-limited version of '" + APPLICATION_NAME + "' has expired.";

        // want this in seconds
        long currentTime = System.currentTimeMillis()/1000;

        // get the hash and epoch value from the resources
        String rsrcExpiryHash = Application.getCurrentApp().getString(kStrExpiryHash).toLowerCase();
        String rsrcExpiryEpoch = Application.getCurrentApp().getCurrentApp().getString(kStrExpiryEpoch);

        // do the hash
        String calcExpiryHash = hash(rsrcExpiryEpoch, secretKey);

        // check the hash to verify that the resource epoch value has
        // not been tampered with
        if (calcExpiryHash.compareTo(rsrcExpiryHash) == 0)
        {
            // check that it has not expired
            if (currentTime < Long.parseLong(rsrcExpiryEpoch))
            {
                return true;
            }
        }

        // failed
        if(showError)
        {
            showError(errorMsg);
        }
        return false;
    }

    public static boolean hasNotReachedMaxUses(String secretKey, boolean showError)
    {
        String errorMsg = "This use-limited version of '" + APPLICATION_NAME + "' has expired.";

        //1.) if the data store is empty then we have never set up security, do so.
        //2.) if the versions are different then we need to reset the security
        if  (securityDataStore.getRecordCount() == 0 ||
            (versionInfo.mPubMajor > getVersionMajor()) ||
            ((versionInfo.mPubMajor == getVersionMajor()) &&
                    ((int)(versionInfo.mPubMinor >> 24) > getVersionMinor())) ||
            ((versionInfo.mPubMajor == getVersionMajor()) &&
                    ((int)(versionInfo.mPubMinor >> 24) == getVersionMinor()) &&
                    (versionInfo.mBuildNumber > getVersionBuild())))
        {
            setUpSecurityDataStore();
        }

        // get the hashed max use value from the resources
        String rsrcUsesHash = Application.getCurrentApp().getString(kStrNumUsesHash).toLowerCase();

        int numUses = getUses();

        // do the hash
        String calcUsesHash = hash(Integer.toString(numUses), secretKey);

        // if the hashes don't match (hasn't reached max uses)
        if (calcUsesHash.compareTo(rsrcUsesHash) != 0)
        {
            incrementUses();
            return true;
        }

        // failed
        if(showError)
        {
            showError(errorMsg);
        }
        return false;
    }


    private static void setUpSecurityDataStore()
    {
        securityDataStore.removeAllRecords();

        //Datastore will be setup as following:
        //1.) uses
        //2.) version
        byte[] dataStoreBytes = new byte[4 +                     //(int) current uses
                                         4 +                     //(int) version major
                                         4 +                     //(int) version minor
                                         4];                     //(int) version build

        int offset = 0;

        ByteArray.writeInt(dataStoreBytes, offset, 0);
        offset += 4;

        ByteArray.writeInt(dataStoreBytes, offset, versionInfo.mPubMajor);
        offset += 4;

        ByteArray.writeInt(dataStoreBytes, offset, (int)(versionInfo.mPubMinor >> 24));
        offset += 4;

        ByteArray.writeInt(dataStoreBytes, offset, versionInfo.mBuildNumber);

        securityDataStore.addRecord(dataStoreBytes);
    }

    protected static void incrementUses()
    {
        byte[] dataStoreBytes = securityDataStore.getRecordData(0);
        ByteArray.writeInt(dataStoreBytes, 0, getUses()+1);
        securityDataStore.addRecord(dataStoreBytes);
    }

    private static int getUses()
    {
        return ByteArray.readInt(securityDataStore.getRecordData(0), 0);
    }

    private static int getVersionMajor()
    {
        return ByteArray.readInt(securityDataStore.getRecordData(0), 4);
    }

    private static int getVersionMinor()
    {
        return ByteArray.readInt(securityDataStore.getRecordData(0), 8);
    }

    private static int getVersionBuild()
    {
        return ByteArray.readInt(securityDataStore.getRecordData(0), 12);
    }

    private static void showError(String message)
    {
        // beep and vibrate - something's wrong, buddy
        Meta.play(Meta.BEEP_ACTION_FAIL);
        AudioManager.vibrate(100);

        MarqueeAlert expired = new MarqueeAlert(message, marqueeIcon);
        NotificationManager warning = new NotificationManager();
        warning.marqueeAlertNotify(expired);
    }

    private static String hash(String data, String secretKey)
    {
        MD5 md5 = new MD5();
        md5.update((data + secretKey + versionInfo.getVersionString(true)).getBytes());
        String hash = StringUtils.bytesToHexString(md5.digest()).toLowerCase();

        return hash;
    }
}
