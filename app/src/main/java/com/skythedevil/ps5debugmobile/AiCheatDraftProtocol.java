package com.skythedevil.ps5debugmobile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Pattern;
/** Validates AI-generated fixed-address patch drafts; never applies them. */
final class AiCheatDraftProtocol {
 private static final Pattern ADDRESS=Pattern.compile("(?i)(0x[0-9a-f]{1,16}|[0-9a-f]{1,16}|[a-z0-9_.-]+\\+0x[0-9a-f]{1,16})");
 private static final Pattern HEX=Pattern.compile("(?i)[0-9a-fx, \\t\\r\\n]+");
 private AiCheatDraftProtocol(){}
 static JSONObject parse(String response){
  if(response==null)return null;
  try{
   int a=response.indexOf("{"),b=response.lastIndexOf("}");if(a<0||b<=a)return null;
   JSONObject root=new JSONObject(response.substring(a,b+1)),action=root.optJSONObject("action");
   if(action==null||!"create_cheat".equals(action.optString("type")))return null;
   JSONObject draft=new JSONObject(action.toString());draft.put("explanation",root.optString("explanation","AI prepared a cheat draft."));
   return validate(draft);
  }catch(Exception e){return null;}
 }
 static JSONObject validate(JSONObject input)throws Exception{
  if(input==null||!"create_cheat".equals(input.optString("type")))throw new IllegalArgumentException("Unsupported action.");
  String name=input.optString("name","").trim();
  if(name.isEmpty()||name.length()>80)throw new IllegalArgumentException("Cheat name must be 1–80 characters.");
  String mode=input.optString("mode","toggle");
  if(!"toggle".equals(mode)&&!"once".equals(mode))throw new IllegalArgumentException("Only toggle and once modes are supported.");
  JSONArray source=input.optJSONArray("steps");
  if(source==null||source.length()<1||source.length()>32)throw new IllegalArgumentException("Cheat must contain 1–32 patch steps.");
  JSONArray clean=new JSONArray();
  for(int i=0;i<source.length();i++){
   JSONObject step=source.optJSONObject(i);
   if(step==null||!"patch".equals(step.optString("op")))throw new IllegalArgumentException("Only fixed-address patch steps are accepted.");
   String addr=step.optString("addr","").trim();
   if(!ADDRESS.matcher(addr).matches())throw new IllegalArgumentException("Invalid address at step "+(i+1)+". Use 0xADDRESS or module+0xOFFSET.");
   if(!"hex".equalsIgnoreCase(step.optString("type","hex")))throw new IllegalArgumentException("Only hex patch values are accepted.");
   String value=step.optString("value","").trim();
   if(value.isEmpty()||value.length()>12288||!HEX.matcher(value).matches())throw new IllegalArgumentException("Invalid hex value at step "+(i+1)+".");
   String compact=value.replaceAll("[, \\t\\r\\n]","").replaceFirst("(?i)^0x","");
   if(compact.isEmpty()||compact.length()%2!=0)throw new IllegalArgumentException("Hex values must contain complete byte pairs.");
   if(compact.length()/2>4096)throw new IllegalArgumentException("Each patch is limited to 4096 bytes.");
   clean.put(new JSONObject().put("op","patch").put("addr",addr).put("type","hex").put("value",compact.toUpperCase(java.util.Locale.US)));
  }
  return new JSONObject().put("name",name).put("mode",mode).put("enabled",false).put("steps",clean);
 }
}
