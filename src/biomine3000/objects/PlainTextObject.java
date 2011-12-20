package biomine3000.objects;

import java.io.UnsupportedEncodingException;

public class PlainTextObject extends BusinessObject {
    
    private String payload;
    
    /** Create unitialized instance. */
    public PlainTextObject() {
        super();
    }
    
    /** Create a plain text business object with mimetype text/plain */ 
    public PlainTextObject(String text) {
        super(BiomineTVMimeType.PLAINTEXT.toString());
        payload = text;               
    }
    
    /** Create a plain text business object with specified mimetype */ 
    public PlainTextObject(String text, String mimeType) {
        super(mimeType);
        payload = text;               
    }
    
    /**
     * Create a plain text business object with specified official mimetype.
     * It is left at the responsibility of the caller that the mimetype actually be representable
     * as a plain text object.
     */  
    public PlainTextObject(String text, BiomineTVMimeType mimeType) {                
        super(mimeType.toString());
        payload = text;               
    }
        
    @Override
    public byte[] getPayload() {
        try {
            return payload.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred; UTF-8 not supported by this very java instance
            throw new RuntimeException("guaqua has been observed to play ZOMBI all night");
        }                                             
    }
    
    @Override
    public void setPayload(byte[] payload) {
        try {
            this.payload = new String(payload, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred
            throw new RuntimeException("leronen has joined facebook");
        } 
    }
    
    public String toString() {
        return metadata.getType()+": "+payload;
    }
            


}
