package biomine3000.objects;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import util.CmdLineArgs2;
import util.DateUtils;
import util.dbg.Logger;
import util.net.NonBlockingSender;

/**
 * Advanced Business Objects Exchange Server.
 * 
 * Reads business objects from each client, broadcasting back everything it reads
 * to all clients.
 * 
 * Two dedicated threads will created for each client, one for sending and one for reading {@link
 * BusinessObject}s.  
 * 
 * Once a client closes its sockets outputstream (the inputstream of the server's socket),
 * the server stops sending to that client and closes the socket. 
 *
 */
public class ABBOEServer {
   
    public static final DateFormat DEFAULT_DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        
    private ServerSocket serverSocket;    
    private int serverPort;
    private List<ClientConnection> clients;
                      
    /** Create server data structures and start listening */
    public ABBOEServer(int port) throws IOException {                                                                   
        this.serverPort = port;
        serverSocket = new ServerSocket(serverPort);        
        clients = new ArrayList<ClientConnection>();
        log("Listening.");
    }                            

    private synchronized void sendToAllClients(byte[] packet) {
        for (ClientConnection client: clients) {
            client.send(packet);
        }
    }          
    
    /** 
     * Should never return. Only way to exit is through client request "stop",
     * {@link UnrecoverableServerException}, or stop signal.
     */
    private void mainLoop() {         
                          
        while (true) {
            log("["+DateUtils.formatDate()+"] waiting for client...");
            
            try {
                Socket clientSocket = serverSocket.accept();
                log("Client connected from "+clientSocket.getRemoteSocketAddress());
                processSingleClient(clientSocket);
            } 
            catch (IOException e) {
                error("Accepting a client failed", e);
            }                                                            
        }                                        
    }
            
    private class ClientConnection implements NonBlockingSender.Listener {
        Socket socket;
        BufferedInputStream is;
        OutputStream os;        
        NonBlockingSender sender;
        ReaderListener readerListener;
        boolean closed;
        String name;
        boolean senderFinished;
        boolean receiverFinished;
        
        ClientConnection(Socket socket) throws IOException {
            senderFinished = false;
            receiverFinished = false;
            this.socket = socket; 
            is = new BufferedInputStream(socket.getInputStream());
            os = socket.getOutputStream();            
            sender = new NonBlockingSender(socket, this);
            readerListener = new ReaderListener(this);
            closed = false;
            name = "ClientConnection-"+socket.getRemoteSocketAddress();
            log("Initialization done");
            synchronized(ABBOEServer.this) {
                clients.add(this);
            }
            
        }        
        
       /**
        * Put object to queue of messages to be sent (to this one client) and return immediately.        
        * Assume send queue has unlimited capacity.
        */
        private void send(byte[] packet) {
            if (senderFinished) {
                warn("No more sending business");
                return;
            }
            
            try {
                sender.send(packet);
            }
            catch (IOException e) {
                error("Failed sending to client "+this, e);
                doSenderFinished();
            }
        }       
        
        private void startReaderThread() {
            BusinessObjectReader readerRunnable = new BusinessObjectReader(is, readerListener, "BusinessObjectReader-"+socket.getRemoteSocketAddress(), false);
            Thread readerThread = new Thread(readerRunnable);
            readerThread.start();
        }               
        
        /**
         * Gracefully dispose of a single client after ensuring both receiver and sender 
         * have finished
         */ 
        private void doClose() {
            if (closed) {
                error("Attempting to close a client multiple times", null);
            }
            
            log("Closing connection with client: "+this);

            try {                                        
                os.flush();
            }
            catch (IOException e) {
                // let's not bother to even log the exception at this stage
            }
            
            try {
                // closing socket also closes streams if needed
                socket.close();
            }
            catch (IOException e) {
                // let's not bother to even log the exception at this stage
            }
            
            synchronized(ABBOEServer.this) {
                clients.remove(this);
                closed = true;
            }
        }                
        
        @Override
        public void senderFinished() {
//            log("SenderListener.finished()");
            doSenderFinished();
//            log("finished SenderListener.finished()");                      
        }
                
        private synchronized void doSenderFinished() {
            log("doSenderFinished");
            if (senderFinished) {
                log("Already done");
                return;
            }
            
            senderFinished = true;
            
            if (receiverFinished) {
                doClose();
            }                                 
        }

        private synchronized void doReceiverFinished() {
            log("doReceiverFinished");
            if (receiverFinished) {
                log("Already done");
                return;
            }
            
            // request stop of sender
            sender.stop();
            
            receiverFinished = true;
            
            if (senderFinished) {
                doClose();
            }                         
        }
        
        private void error(String msg, Exception e) {
            Logger.error(this+": "+msg, e);
        }
        
        private void log(String msg) {
            Logger.info(this+": "+msg);
        }
        
        public String toString() {
            return name;
        }
    }
                    
    private void processSingleClient(Socket clientSocket) {
        
        ClientConnection client;
         
        try {
            client = new ClientConnection(clientSocket);
        }
        catch (IOException e) {
            error("Failed creating streams on socket", e);
            try {
                clientSocket.close();
            }
            catch (IOException e2) {
                // failed even this, no further action possible
            }
            return;
        }        

        client.startReaderThread();
    }          
    
    @SuppressWarnings("unused")
    private void terminate(int pExitCode) {
        // TODO: more delicate termination needed?
        log("Shutting down the server at "+DateUtils.formatDate());
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            Logger.warning("Error while closing server socket in cleanUp(): ", e);                            
        }
        System.exit(pExitCode);
    }
                     
    /** Listens to a single dedicated reader thread reading objects from the input stream of a single client */
    private class ReaderListener implements BusinessObjectReader.Listener {
        ClientConnection client;
        
        ReaderListener(ClientConnection client) {
            this.client = client;
        }

        @Override
        public void objectReceived(BusinessObject bo) {
            log("Received business object: "+bo);
            log("Sending the very same object...");
            ABBOEServer.this.sendToAllClients(bo.bytes());
            // client.send(bo.bytes());
        }

        @Override
        public void noMoreObjects() {
            log("noMoreObjects (client closed connection).");                                  
            client.doReceiverFinished();            
        }

        private void handleException(Exception e) {            
            error("Exception while reading objects from client "+client, e);                                            
            client.doReceiverFinished();
        }
        
        @Override
        public void handle(IOException e) {
            handleException(e);
        }

        @Override
        public void handle(InvalidPacketException e) {
            handleException(e);
            
        }

        @Override
        public void handle(BusinessObjectException e) {
            handleException(e);            
        }

        @Override
        public void handle(RuntimeException e) {
            handleException(e);
            
        }        
    }
                    
    public static void main(String[] pArgs) throws Exception {
        
        CmdLineArgs2 args = new CmdLineArgs2(pArgs);
                        
        Integer port = args.getIntOpt("port");
        
        if (port == null) {             
            port = Biomine3000Utils.conjurePortByHostName();
        }
        
        if (port == null) {
            error("No port");
            System.exit(1);
        }
        
        log("Starting test server at port "+port +" ("+DateUtils.formatOrderableDate()+")");
                       
        try {
            ABBOEServer server = new ABBOEServer(port);
            server.mainLoop();
        }
        catch (IOException e) {
            error("Failed initializing server", e);
        }
    }
    
    private static void log(String msg) {
        Logger.info("TestServer: "+msg);
    }    
    
    private static void warn(String msg) {
        Logger.warning("TestServer: "+msg);
    }        
    
    private static void error(String msg) {
        Logger.error("TestServer: "+msg);
    }
    
    private static void error(String msg, Exception e) {
        Logger.error("TestServer: "+msg, e);
    }
          
}