
import java.io.*; 
import java.net.*; 
  
import java.io.File;
import java.nio.file.Files;

import java.util.Random;

import java.nio.ByteBuffer;
import java.math.BigInteger;

import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


class UDPClient {
    static int totalbytestotransfer;
    static float bytesSinceLastOutput = 0;
    static int bytesTransfered;

    static class Datarate_Output extends TimerTask {
        public void run() {
            System.out.printf("Datenrate(vergangene Sekunde):%6.3f KB/s, ausstehende Infobytes: %8d  \r",
                    bytesSinceLastOutput /1000, totalbytestotransfer - bytesTransfered
                    );
            bytesSinceLastOutput = 0;
        }
    }
    
    public static void main(String args[]) throws Exception 
    {
      boolean debuginfo = false;

      if(args.length < 3){
            System.out.println("Client: Es fehlt an Argumenten.");
            System.exit(-1);
      }
        

      int timeout_cnt = 0;
      Timer timer = new Timer(); //Output Datarate
      //int host = Integer.parseInt(args[0]);
      int port = Integer.parseInt(args[1]);
      
      
      File in_file = new File(args[2]);
      String filename = in_file.getName();

      if(!in_file.exists())
      {
        System.out.println("Client: Zu kopierender File existiert nicht.");
        System.exit(-1);
      }

      byte[] filebytes = Files.readAllBytes(in_file.toPath());
      totalbytestotransfer = filebytes.length;
      int mtu = 1500;
 
      DatagramSocket clientSocket = new DatagramSocket(); 
      
      int actualTimeout = 1000;
      //Timeout setzen
      clientSocket.setSoTimeout(actualTimeout);
      
      InetAddress IPAddress = InetAddress.getByName(args[0]); 

      int successCnt = -1; //wegen Startpaket

      byte[] sendData = new byte[mtu];
      byte[] receiveData = new byte[mtu];
  
      byte[] sessionnummer = new byte[2];
      
      DatagramPacket sendPacket;
  
        int i = 0;
        long transferTime_start = 0;

        int lastpacketsize = filebytes.length % 1497;
        int secondlastsize = 1497;

        long n_packets = (filebytes.length - lastpacketsize)/1497;
        if(lastpacketsize > 1493) {
            n_packets++;
            secondlastsize = 1493;
            lastpacketsize = lastpacketsize - secondlastsize;
        }


        byte[] startpaket = new byte[19+256];

        

        //starte zeitmessung für gesamte datei
        transferTime_start = System.currentTimeMillis();

        int paketnummer = 0; //wegen startpaket
        while(successCnt < n_packets + 1)
        {
                if(debuginfo)
                    System.out.println("Client: Durchlauf: " + i);
                
                if(i == 0)
                {
                //16-Bit Sessionnummer (Wahl per Zufallsgenerator)
                    new Random().nextBytes(startpaket);
                    new Random().nextBytes(startpaket);
                    
                    sessionnummer[0] = startpaket[0];
                    sessionnummer[1] = startpaket[1];
                //8-Bit Paketnummer (immer 0)
                    startpaket[2] = 0;
                //5-Byte Kennung „Start“ als ASCII-Zeichen
                    startpaket[3] = 'S';
                    startpaket[4] = 't';
                    startpaket[5] = 'a';
                    startpaket[6] = 'r';
                    startpaket[7] = 't';
                //64-Bit Dateilänge (unsigned integer) (für Dateien > 4 GB)
                    //byte[] bytes = ByteBuffer.allocate(8).putLong(2147483647).array(); 
                    BigInteger bd = new BigInteger(String.valueOf(filebytes.length));
                    byte[] bt = bd.toByteArray();
                
                   
                
                    int b = 0;
                    for(int a = 0; a < 8; a++)
                    {
                        if(a < 8 - bt.length)
                            startpaket[8+a] = 0;
                        else
                        {
                            startpaket[8+a] = bt[b];
                            b++;
                        }
                    }
                    
                    String st =  String.format("%8s", Long.toBinaryString(startpaket[15] & 0xFF)).replace(' ', '0');
                    //System.out.println("size: " + st);

            //2 Byte (unsigned integer) Länge des Dateinamens
                    byte[] bytes = ByteBuffer.allocate(2).putShort((short)filename.length()).array();
                    startpaket[16] = bytes[0];
                    startpaket[17] = bytes[1];
            //0-255 Byte Dateiname als String mit Codierung UTF-8
                    byte[] utf8name = filename.getBytes("UTF-8");
                    int utf8name_length = utf8name.length; 
                    for(int z = 0; z < utf8name_length; z++)
                    {
                        startpaket[18+z] = utf8name[z];
                    }
            //32-Bit-CRC über alle Daten des Startpaketes
                    
                    Checksum checksum = new CRC32();// Polynom wohl automatisch 0x04C11DB7 ?
                    // update the current checksum with the specified array of bytes
                    checksum.update(startpaket, 0, 18 + utf8name_length); //funktioniert

                    System.out.println("Client: CRC Startpaket: " + checksum.getValue());
                    System.out.println("Warte auf Antwort vom Server..\n");

                    byte[] checkbytes = ByteBuffer.allocate(4).putInt((int)checksum.getValue()).array();
                    
                    int arrayindex = 18 + utf8name_length;
                    startpaket[arrayindex  ] = checkbytes[0];
                    startpaket[arrayindex+1] = checkbytes[1];
                    startpaket[arrayindex+2] = checkbytes[2];
                    startpaket[arrayindex+3] = checkbytes[3];

                    sendPacket = 
                            new DatagramPacket(startpaket, startpaket.length, IPAddress, port); 

                }
                else
                {      
                ////Datenpakete (Client -> Server)
                    
                    //16-Bit-Sessionnummer
                    byte[] packet = new byte[mtu];
                    sendData[0] = sessionnummer[0];
                    sendData[1] = sessionnummer[1];
                    //8-Bit Paketnummer ( 1. Datenpaket hat die Nr. 1, gerechnet wird mod 2, also nur 0 und 1)
                    sendData[2] = (byte)(paketnummer);
                    //Daten  
                    
                    if(successCnt == n_packets)
                    {
                        System.arraycopy(filebytes,
                                        (successCnt - 1) * 1497 + secondlastsize,
                                        sendData,
                                        3,
                                        lastpacketsize);

                        //CRC über gesamtdatei
                        Checksum filechecksum = new CRC32();// Polynom: 0x04C11DB7


                        // update the current checksum with the specified array of bytes
                        filechecksum.update(filebytes, 0, filebytes.length); //funktioniert
                        if(debuginfo)
                            System.out.println("\nClient: CRC über Datei: " + filechecksum.getValue());
                        byte[] filecheckbytes = ByteBuffer.allocate(4).putInt((int)filechecksum.getValue()).array();

                        System.arraycopy(filecheckbytes, 
                                        0,
                                        sendData,
                                        lastpacketsize + 3,
                                        4);
                        
                        sendPacket = 
                                new DatagramPacket(sendData, lastpacketsize+3+4, IPAddress, port);
                    }
                    else if (successCnt == n_packets - 1)
                    {
                        System.arraycopy(filebytes,
                                        successCnt * 1497,
                                        sendData,
                                        3,
                                        secondlastsize);


                        if(debuginfo)
                            System.out.println("Client: Neues Paket mit Größe: " + sendData.length +
                                    "\nTimeout in ms: " + actualTimeout +
                                    "\nSucesscnt: " + successCnt);

                            sendPacket =
                                new DatagramPacket(sendData, sendData.length, IPAddress, port);
                    }
                    else
                    {
                        System.arraycopy(filebytes,
                                successCnt * 1497,
                                sendData,
                                3,
                                mtu - 3);


                        if(debuginfo)
                            System.out.println("Client: Neues Paket mit Größe: " + sendData.length +
                                    "\nTimeout in ms: " + actualTimeout +
                                    "\nSucesscnt: " + successCnt);

                        sendPacket =
                                new DatagramPacket(sendData, sendData.length, IPAddress, port);
                    }
                    
                    
                    
                
                    
                        
                }
                
                DatagramPacket receivePacket = 
                               new DatagramPacket(receiveData, receiveData.length);



                long startTime;
                while(true)
                {
                    startTime = System.currentTimeMillis();

                    clientSocket.send(sendPacket);

                    try{
                        clientSocket.receive(receivePacket);

                        if(receiveData[2] == paketnummer)
                        {
                            if(debuginfo)
                                System.out.println("Bestätigung erhalten, ACK : " + paketnummer);

                            paketnummer = (paketnummer + 1) % 2;
                            successCnt++;
                            i++;



                            if(successCnt == n_packets)
                                bytesTransfered += lastpacketsize;
                            else if(successCnt == n_packets - 1)
                                bytesTransfered += secondlastsize;
                            else
                                bytesTransfered += mtu-3;

                            long stopTime = System.currentTimeMillis();
                            long elapsedTime = stopTime - startTime;
                            bytesSinceLastOutput += 1500;

                            timeout_cnt = 0;

                            clientSocket.setSoTimeout(actualTimeout);

                            if(successCnt == 0) { //Starte Timer erst nach erfolgreichem Verbindungsuafbau
                                //Timer starten, der jede Sekunde die klasse Datarate_Output aufruft, welche die aktuelle Datenrate ausgibt
                                timer.schedule(new Datarate_Output(), 1000, 1000);
                            }
                            break;
                        }
                    }
                    catch (SocketTimeoutException e) {
                        if(debuginfo)
                            System.out.println("\nClient: Timeout exception");

                        timeout_cnt++;

                        if(timeout_cnt == 10)
                        {
                            System.out.println("\n\nClient: Vorgang abgebrochen(10 Timeouts)\r");
                            System.exit(-1);
                        }

                        clientSocket.setSoTimeout(actualTimeout);
                    }

                }
                        

                        
                        
                    String sNumber =  String.format("%8s", Long.toBinaryString(receiveData[0] & 0xFF)).replace(' ', '0')
                    + String.format("%8s", Long.toBinaryString(receiveData[1] & 0xFF)).replace(' ', '0');
                
                    if(debuginfo)
                    {
                        System.out.println("\nClient: Schleifendurchlauf:" + i + " " );
                        System.out.println("Client: Sessionnummer: " + Long.parseLong(sNumber,2));
                        System.out.println("Client: Ack: " + String.format("%8s", Byte.toString(receiveData[2])) + "\n\n");
                    }
        }

        timer.cancel();

        long transferTime = System.currentTimeMillis() - transferTime_start;

        long n_totalbytes = n_packets * 1500 + lastpacketsize + 4 + 3 + startpaket.length;

        if(debuginfo)
            System.out.println("Last Packet: " + lastpacketsize);

        System.out.println("\n\nGesamte Bytes(mit overhead): " +  n_totalbytes);
        System.out.println("Gesamte Übertragungszeit: " +  transferTime + "ms\n");
        System.out.printf("Datenübertragungsrate(Brutto): %.3f KB/s\n", n_totalbytes/((float)transferTime/1000) /1000);
        System.out.printf("Durchsatzrate(Netto): %.3f KB/s\n\n", filebytes.length/((float)transferTime/1000) /1000);

        //Datum ausgeben
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        System.out.print(dateFormat.format(date) +  "(");
        System.out.printf(" %.3f KB/s)", n_totalbytes/((float)transferTime/1000) /1000);
        System.out.println(" - <Kopie von " +
                filename + "> gespeichert [" + filebytes.length + "]"
                );




        clientSocket.close();
      } 
} 	  
