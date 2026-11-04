package com.skythedevil.ps5debugmobile;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Pattern;
/** Strict AI memory action protocol. All actions are proposals; MainActivity enforces candidate provenance and user approval. */
final class AiMemoryActionProtocol {
    private static final Pattern ADDRESS=Pattern.compile("(?i)(0x[0-9a-f]{1,16}|[0-9]{1,20})");
    private static final Pattern HEX=Pattern.compile("(?i)[0-9a-f, \\t\\r\\n]+");
    static final class Proposal {
        final String type,address,explanation; final int length; final byte[] bytes;
        Proposal(String t,String a,int n,byte[] b,String e){type=t;address=a;length=n;bytes=b;explanation=e;}
        String summary(){return "read_memory".equals(type)?"Read "+length+" byte(s) at "+address+" (read-only)":"Write "+bytes.length+" byte(s) at "+address+" (requires live preview + confirmation)";}
    }
    private AiMemoryActionProtocol(){}
    static Proposal parse(String response){
        if(response==null)return null;
        try{
            int a=response.indexOf("{"),b=response.lastIndexOf("}");
            if(a<0||b<=a)return null;
            JSONObject root=new JSONObject(response.substring(a,b+1)),action=root.optJSONObject("action");
            if(action==null)return null;
            String type=action.optString("type","");
            if(!"read_memory".equals(type)&&!"write_memory".equals(type))return null;
            String address=action.optString("addr","").trim();
            if(!ADDRESS.matcher(address).matches())return null;
            if("read_memory".equals(type)){
                int length=action.optInt("length",0);
                if(length<1||length>256)return null;
                return new Proposal(type,address,length,new byte[0],root.optString("explanation","AI proposed a read-only memory check."));
            }
            String value=action.optString("value","").trim();
            if(value.isEmpty()||value.length()>768||!HEX.matcher(value).matches())return null;
            String compact=value.replaceAll("[, \\t\\r\\n]","");
            if(compact.isEmpty()||(compact.length()&1)!=0||compact.length()/2>256)return null;
            byte[] bytes=new byte[compact.length()/2];
            for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(compact.substring(i*2,i*2+2),16);
            return new Proposal(type,address,bytes.length,bytes,root.optString("explanation","AI proposed a memory write."));
        }catch(Exception ignored){return null;}
    }
}
