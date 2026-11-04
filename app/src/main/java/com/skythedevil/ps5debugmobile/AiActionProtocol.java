package com.skythedevil.ps5debugmobile;
import org.json.JSONObject;
/** Strict parser for read-only AI-proposed scanner actions. */
final class AiActionProtocol {
 static final class Proposal {
  final String explanation,value; final int valueType,compare;
  Proposal(String e,String v,int t,int c){explanation=e;value=v;valueType=t;compare=c;}
  String summary(){
   String[] types={"Byte (unsigned)","Byte (signed)","2 bytes (unsigned)","2 bytes (signed)","4 bytes (unsigned)","4 bytes (signed)","8 bytes (unsigned)","8 bytes (signed)","Float","Double"};
   String[] modes={"Exact value","Fuzzy (±1)","Bigger than","Smaller than","Between low/high","Increased","Increased by","Decreased","Decreased by","Changed","Unchanged","Unknown initial value"};
   return "Type: "+types[valueType]+"\\nComparison: "+modes[compare]+"\\nValue: "+(value.isEmpty()?"(not required)":value);
  }
 }
 private AiActionProtocol(){}
 static Proposal parse(String response){
  if(response==null)return null;
  try{
   int a=response.indexOf("{"),b=response.lastIndexOf("}"); if(a<0||b<=a)return null;
   JSONObject root=new JSONObject(response.substring(a,b+1)), action=root.optJSONObject("action");
   if(action==null||!"turbo_scan".equals(action.optString("type")))return null;
   int type=action.getInt("value_type"),compare=action.getInt("comparison");
   if(type<0||type>9||compare<0||compare>11)return null;
   String value=action.optString("value",""); if(value.length()>128)return null;
   if(compare==0||compare==2||compare==3||compare==4||compare==6||compare==8)if(value.trim().isEmpty())return null;
   if(compare==4){String[] p=value.split(",",-1);if(p.length!=2)return null;Double.parseDouble(p[0].trim());Double.parseDouble(p[1].trim());}
   return new Proposal(root.optString("explanation","AI proposed a scan."),value,type,compare);
  }catch(Exception e){return null;}
 }
}
