package biomine3000.objects;

public class ErrorObject extends BusinessObject {
        
    /** Create unitialized instance. */
    public ErrorObject() {
        super();
        getMetaData().setEvent(BusinessObjectEventType.ERROR);
    }
        
    public ErrorObject(String text) {
        super(Biomine3000Mimetype.PLAINTEXT.toString());
        getMetaData().setEvent(BusinessObjectEventType.ERROR);
        this.setPayload(text.getBytes());               
    }
        
    public ErrorObject(String text, String mimeType) {
        super(mimeType);
        getMetaData().setEvent(BusinessObjectEventType.ERROR);
        this.setPayload(text.getBytes());
    }
    
    /**
     * Create and error business object with specified official mimetype.
     * It is left at the responsibility of the caller that the mimetype actually be representable
     * as a plain text object.
     */  
    public ErrorObject(String text, Biomine3000Mimetype mimeType) {                
        super(mimeType.toString());
        getMetaData().setEvent(BusinessObjectEventType.ERROR);
        this.setPayload(text.getBytes());
    }
    
    public String getText() {
        return new String(this.getPayload());
    }
                      
    public String toString() {
        return "ERROR ("+getMetaData().getType()+": "+getText()+")";
    }            

}

