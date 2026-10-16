// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
public class CloudCredentials {
    public boolean isPullOnly(){return prefs.getBoolean("pull_only",true);}
    public final SharedPreferences prefs;
    public CloudCredentials(Context ctx){prefs=ctx.getSharedPreferences("cloud-sync",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias("rimecloud-s3")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("rimecloud-s3",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (SecretKey)store.getKey("rimecloud-s3",null);}
    public void save(String access,String secret) throws Exception {if(access.trim().isEmpty()||secret.isEmpty())throw new IllegalArgumentException("请填写 S3 两个密钥");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());String encrypted=Base64.encodeToString(c.getIV(),2)+":"+Base64.encodeToString(c.doFinal(secret.getBytes(StandardCharsets.UTF_8)),2);prefs.edit().putString("access",access.trim()).putString("secret",encrypted).commit();}
    public String access(){String value=prefs.getString("access","");if(value.isEmpty())throw new IllegalStateException("请先保存 S3 密钥");return value;}
    public String secret() throws Exception {String saved=prefs.getString("secret","");if(saved.isEmpty())throw new IllegalStateException("请先保存 S3 密钥");String[] parts=saved.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],2)));return new String(c.doFinal(Base64.decode(parts[1],2)),StandardCharsets.UTF_8);}
}
