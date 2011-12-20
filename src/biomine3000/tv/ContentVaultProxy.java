package biomine3000.tv;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import javax.imageio.ImageIO;

import util.CygwinUtils;
import util.RandUtils;
import util.dbg.Logger;


/**
 * A proxy to a remote content vault. This trivial version loads all content, and notifies listeners after each piece of content has been 
 * loaded.
 */
public class ContentVaultProxy {
    
    public static String LERONEN_IMAGE_VAULT_URL = "http://www.cs.helsinki.fi/u/leronen/biomine3000/biomine_tv_image_vault";
    public static String LERONEN_IMAGE_VAULT_FILELIST_URL = LERONEN_IMAGE_VAULT_URL+"/filelist.txt";
    // public static String BAGGER_URL = "http://www.cs.helsinki.fi/u/leronen/biomine3000/biomine_tv_image_vault/bagger2.jpg";
    
    /** only contains successfully loaded images */
    private State state;
    private List<String> urls;
    private Map<String, BufferedImage> loadedImagesByURL;
    private List<ContentVaultListener> listeners;
    
    /**
     * Create and uninitialized vault proxy with no images. Do not start loading images yet
     * until called for.
     *
     */
    public ContentVaultProxy() {
        state = State.UNINITIALIZED;
        loadedImagesByURL = new TreeMap<String,BufferedImage>();
        listeners = new ArrayList<ContentVaultListener>();
    }
        
    public int getNumLoadedObjects() {
        return loadedImagesByURL.keySet().size();
    }
    
    public int getTotalNumObjects() {
        return urls.size(); 
    }
    
    public void addListener(ContentVaultListener vaultListener) {
        listeners.add(vaultListener);
    }
    
    public void removeListener(ContentVaultListener vaultListener) {
        listeners.remove(vaultListener);
    }
    
    /** Returns immediately */
    public void startLoading() {
        new Thread(new Loader()).start();        
    }
    
    /**
     * Should only be called if called is certain that images have been loaded.
     * Never return null.
     */
    public BufferedImage sampleImage() throws InvalidStateException {
        synchronized(loadedImagesByURL) {    
            if (loadedImagesByURL == null || loadedImagesByURL.size() == 0) {
                throw new InvalidStateException("No images to sample from");
            }
            
            String url = RandUtils.sample(loadedImagesByURL.keySet());
            BufferedImage image = loadedImagesByURL.get(url);
            return image;
        }            
    }
    
    /** Load list of available images from a remote file. */
    private static List<String> loadImageList(String filelistURLString) throws IOException {
        List<String> result = new ArrayList<String>();
        URL url= new URL(filelistURLString);
        String protocol = url.getProtocol();
        String hostName = url.getHost();               
        File path = new File(url.getPath());
        String dir = path.getParent().replace('\\', '/'); // windows...        
        String baseName = protocol+"://"+hostName+dir;
                        
        try {     
            // Create a URL for the desired page                   
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));            
            String name;
            while ((name = in.readLine()) != null) {     
                String imageURL = baseName+"/"+name;
                // Logger.info("Imageurl: "+imageURL);
                result.add(imageURL);
            }   
            return result;
   
        } catch (IOException e) {
            Logger.error("Could now read file list from image vault", e);
            return result;
        }
            
    }
    
    /**
     * @return a path to leronen image vault under a local leronen svn checkout, if one exists; otherwise null.
     * Based on the assumption that location of leronen svn chekout is specified by env variable LERONEN_SVN,
     * and the image vault dir under that is "leronen_image_vault". 
     */ 
//    public static String getLeronenImageVaultDir() {
//        // currently only works on leronen local machine        
//        String leronenSvnPath = System.getenv("LERONEN_SVN");
//        File leronenSvnDir = new File(leronenSvnPath);
//        
//        if (!leronenSvnDir.exists() && CygwinUtils.isCygwin()) {
//            // OK, try cygwin kludges
//            leronenSvnPath = CygwinUtils.cygwinPathToWindowsPath(leronenSvnPath);
//            leronenSvnDir = new File(leronenSvnPath);
//            Logger.info("Converted LERONEN_SVN to winPath: "+leronenSvnPath);            
//            Logger.info(leronenSvnPath+" exists after conversion: "+leronenSvnDir.exists());
//        }
//                                              
//        String imageVault = leronenSvnDir+File.separator+"leronen_image_vault";
//        
//        if (new File(imageVault).exists()) {
//            return imageVault;
//        }
//        else {
//            Logger.info("No such image vault: "+imageVault);
//            return null;
//        }
//                        
//    }
               
    private class Loader implements Runnable {
        public void run() {
            if (state != State.UNINITIALIZED) {
                throw new RuntimeException("Should only be called when state is "+State.UNINITIALIZED);
            }
            
            state = State.LOADING_FILELIST;
                        
            try {
                urls = loadImageList(LERONEN_IMAGE_VAULT_FILELIST_URL);
                state = State.LOADING_IMAGES;
                Logger.info("Filelist loaded");
                for (ContentVaultListener listener: listeners) {
                    listener.loadedImageList();
                }
            }
            catch (IOException e) {
                state = State.FAILED_LOADING_FILELIST;
                Logger.warning("Failed loading filelist", e);
                return;
            }
            
            for (String url: urls) {
                // Logger.info("Loading image: "+url);
                BufferedImage img = null;
                try {
                    img = ImageIO.read(new URL(url));
                    // allow main thread to proceed                    
                    // Logger.info("Loaded image: "+url);
                    synchronized(loadedImagesByURL) {                    
                        loadedImagesByURL.put(url,  img);
                    }
                    for (ContentVaultListener listener: listeners) {
                        listener.loadedImage(url);
                    }
                }
                catch (IOException e) {
                    Logger.warning("Failed loading image: "+url, e);                
                }                
            }
                                                  
            if (loadedImagesByURL.size() == 0) {
                state = State.FAILED_LOADING_IMAGES;
                Logger.error("Failed to load any images");
            }
            else {            
                state = State.INITIALIZED_SUCCESSFULLY;
                Logger.info("Loaded "+loadedImagesByURL.keySet().size()+"/"+urls.size()+" images");
            }
        }
    }
            
    public interface ContentVaultListener {
        /**
         * Called after vault has loaded the list of images. Note that caller is reponsible 
         * for doing the actual responding in a synchronized way (more spefifically, this 
         * will not be called from the event dispatch thread)
         */
        public void loadedImageList();
        
       /** Called after vault has loaded each image.
         * Note that caller is reponsible for doing the actual responding in a synchronized way (more spefifically, this 
         * will not be called from the event dispatch thread)
         */
        public void loadedImage(String image);                   
    }
    
    public static void main(String[] args) throws IOException {
        List<String> urls = loadImageList(LERONEN_IMAGE_VAULT_FILELIST_URL);
        for (String url: urls) {
            System.out.println(url);
        }
        
        ContentVaultProxy content = new ContentVaultProxy();
        content.startLoading();
//        System.out.println(""+getLeronenImageVaultDir());
//                
//        for (String url: getLeronenImageVaultImageURLs()) {
//            System.out.println(url);
//        }
    }
    
//    /** State of each initialized image */
//    public enum IMState {
//        UNINITIALIZED,
//        LOADING,
//        FAILED,
//        FILELIST_LOADED;
//    }
    
    /** State of vault */
    public enum State {
        UNINITIALIZED,
        LOADING_FILELIST,
        LOADING_IMAGES,
        FAILED_LOADING_FILELIST,
        FAILED_LOADING_IMAGES,
        INITIALIZED_SUCCESSFULLY;
        
    }
    
    public class InvalidStateException extends Exception {
        public InvalidStateException(String msg) {
            super(msg);
        }
    }
    
}
