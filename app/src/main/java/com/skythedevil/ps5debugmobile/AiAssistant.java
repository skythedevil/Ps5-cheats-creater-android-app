package com.skythedevil.ps5debugmobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** OpenAI-compatible chat transport with an Android Keystore protected API key. */
public final class AiAssistant {
    private static final String PREFS="ai_assistant_config";
    private static final String ALIAS="ps5_debug_studio_ai_key_v1";
    private static final String DEFAULT_ENDPOINT="https://api.openai.com/v1";
    private static final String DEFAULT_MODEL="gpt-4o-mini";
    private AiAssistant(){}

    public static String defaultEndpoint(){return DEFAULT_ENDPOINT;}
    public static String defaultModel(){return DEFAULT_MODEL;}
    public static String endpoint(Context c){return c.getSharedPreferences(PREFS,0).getString("endpoint",DEFAULT_ENDPOINT);}
    public static String model(Context c){return c.getSharedPreferences(PREFS,0).getString("model",DEFAULT_MODEL);}
    public static boolean isConfigured(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,0);
        return p.contains("key_cipher")&&p.contains("key_iv");
    }
    public static void saveConfig(Context c,String endpoint,String model,String apiKey)throws Exception{
        SharedPreferences p=c.getSharedPreferences(PREFS,0);
        SharedPreferences.Editor e=p.edit();
        String ep=endpoint==null?"":endpoint.trim(), md=model==null?"":model.trim();
        e.putString("endpoint",ep.isEmpty()?DEFAULT_ENDPOINT:ep);
        e.putString("model",md.isEmpty()?DEFAULT_MODEL:md);
        if(apiKey!=null&&!apiKey.trim().isEmpty()){
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,getOrCreateKey());
            byte[] encrypted=cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            e.putString("key_cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP));
            e.putString("key_iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
        }
        if(!e.commit())throw new IOException("Could not save AI settings.");
    }
    public static void clearConfig(Context c){
        c.getSharedPreferences(PREFS,0).edit().clear().commit();
        try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);ks.deleteEntry(ALIAS);}catch(Exception ignored){}
    }
    private static SecretKey getOrCreateKey()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }
    private static String apiKey(Context c)throws Exception{
        SharedPreferences p=c.getSharedPreferences(PREFS,0);
        String cipherText=p.getString("key_cipher",null),ivText=p.getString("key_iv",null);
        if(cipherText==null||ivText==null)throw new IOException("Save an AI API key in AI Settings first.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,Base64.decode(ivText,Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(cipherText,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    /** Sends the last user message with an attached JPEG data URL to a vision-capable OpenAI-compatible model. */
    public static String chatWithImage(Context c,JSONArray messages,String imageDataUrl)throws Exception{
        if(messages==null||messages.length()==0)throw new IOException("Add a screen description before attaching an image.");
        if(imageDataUrl==null||!imageDataUrl.startsWith("data:image/jpeg;base64,")||imageDataUrl.length()>7*1024*1024)
            throw new IOException("Invalid or oversized screen image.");
        JSONArray copy=new JSONArray();
        for(int i=0;i<messages.length();i++){
            JSONObject original=messages.getJSONObject(i);
            JSONObject item=new JSONObject(original.toString());
            if(i==messages.length()-1&&"user".equals(item.optString("role"))){
                String prompt=item.optString("content","");
                JSONArray content=new JSONArray();
                content.put(new JSONObject().put("type","text").put("text",prompt));
                content.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",imageDataUrl).put("detail","high")));
                item.put("content",content);
            }
            copy.put(item);
        }
        return chat(c,copy);
    }
    public static String chat(Context c,JSONArray messages)throws Exception{
        String endpoint=endpoint(c).replaceAll("/+$","");
        if(!endpoint.endsWith("/chat/completions"))endpoint+="/chat/completions";
        JSONObject body=new JSONObject();
        body.put("model",model(c));body.put("messages",messages);body.put("temperature",0.2);
        byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        try{
            connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(60000);
            connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
            connection.setRequestProperty("Authorization","Bearer "+apiKey(c));
            if(new URL(endpoint).getHost().equalsIgnoreCase("openrouter.ai")){
                connection.setRequestProperty("HTTP-Referer","https://github.com/skythedevil/Ps5-cheats-creater-android-app");
                connection.setRequestProperty("X-Title","PS5 Debug Studio");
            }
            connection.getOutputStream().write(payload);
            int code=connection.getResponseCode();
            InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
            while((n=stream.read(buf))!=-1){out.write(buf,0,n);if(out.size()>2*1024*1024)throw new IOException("AI response exceeded 2 MiB.");}
            String response=new String(out.toByteArray(),StandardCharsets.UTF_8);
            if(code<200||code>=300)throw new IOException("AI HTTP "+code+": "+response.substring(0,Math.min(700,response.length())));
            JSONObject root=new JSONObject(response);
            JSONArray choices=root.optJSONArray("choices");
            if(choices==null||choices.length()==0)throw new IOException("AI returned no response choices.");
            JSONObject message=choices.getJSONObject(0).getJSONObject("message");
            Object content=message.opt("content");
            if(content instanceof String)return (String)content;
            if(content instanceof JSONArray){
                StringBuilder text=new StringBuilder();JSONArray parts=(JSONArray)content;
                for(int i=0;i<parts.length();i++){JSONObject part=parts.optJSONObject(i);if(part!=null)text.append(part.optString("text",""));}
                if(text.length()>0)return text.toString();
            }
            throw new IOException("AI response content was empty or unsupported.");
        }finally{connection.disconnect();}
    }
}
