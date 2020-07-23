import java.io.*; 
import java.net.*; 
  
import java.io.File;
import java.nio.file.Files;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.math.BigInteger;


class UDPServer {
    public static void main(String args[]) throws Exception
    {
      boolean debuginfo = false;

      if(args.length < 1){
            System.out.println("Es fehlt an Argumenten");
            System.exit(-1);
      }
      int delayTime = 0;
      int delay_input = 0;

      byte[] sessionnr = new byte[2];

      if(args.length > 2)
      {
        delayTime = Integer.parseInt(args[2]);
        delay_input = delayTime;
      }
      int ownport = Integer.parseInt(args[0]);
     
      DatagramSocket serverSocket = new DatagramSocket(ownport); 
  
      byte[] receiveData = new byte[1500]; 
      byte[] sendData  = new byte[1500];
      DatagramPacket sendPacket;

      
//receive first packet
//==================================================================================================================================

      int cnt = 0;
      
      BigInteger srcsize = new BigInteger("0");
      long n_packets = 0;
      
      String dateiname = "";
      
       DatagramPacket receivePacket0 =  new DatagramPacket(receiveData, receiveData.length);

        //Verögerung simulieren:
          if(delayTime>0) {
              System.out.println("sleep");
              TimeUnit.MILLISECONDS.sleep(delayTime);
              delayTime = delay_input  + (new Random().nextInt() % (delay_input / 10)); //sinnvolle abweichung wählen
           }
        //Paketverlust simulieren:
        float loss_prob = 0;
        int timesToLoss = 0;
        
        if(args.length > 1)
        {
            loss_prob = Float.parseFloat(args[1]);
            timesToLoss = (int)(1/loss_prob);
        }



        System.out.println("Server: Waiting for new connection...");


        serverSocket.receive(receivePacket0);

        if(delayTime>0) {
            //Verögerung simulieren:
            TimeUnit.MILLISECONDS.sleep(delayTime);
            delayTime = delay_input  + (new Random().nextInt() % (delay_input / 10)); //sinnvolle abweichung wählen
        }

        //System.out.println("Packet received: " + cnt);
        
        InetAddress IPAddress = receivePacket0.getAddress();

        int port = receivePacket0.getPort();
        
        //System.out.println(receiveData.length);
        
        String str_validChecksum = "";
        Checksum checksum = new CRC32();// Polynom automatisch 0x04C11DB7

        sessionnr[0] = receiveData[0];
        sessionnr[1] = receiveData[1];

        //Prüfen, ob "Start"-Kennzeichnung übertragen wurde
            if(! (
                receiveData[3] == 'S' &&
                receiveData[4] == 't' &&
                receiveData[5] == 'a' &&
                receiveData[6] == 'r' &&
                receiveData[7] == 't'))
            {
                System.out.println("Erhaltenes Paket nicht als Startpaket erkannt. Abbruch.");
                System.exit(-1);
            }


            String s1 = String.format("%8s", Integer.toBinaryString(receiveData[16] & 0xFF)).replace(' ', '0');
            String s2 = String.format("%8s", Integer.toBinaryString(receiveData[17] & 0xFF)).replace(' ', '0');
            String str = s1+s2;
            int l = Integer.parseInt(str, 2);

            if(debuginfo)
                System.out.println("Länge des Dateinamens: " + l);

            for(int z = 0; z < l; z++)
            {
                dateiname += (char)receiveData[18+z];
            }
            
            while(new File(dateiname).exists())
            {
            //trenne dateiname um "1" an vorderen teil anhängen zu können
                if(dateiname.contains(".")) {
                    String fname1 = dateiname.split("\\.")[0];
                    String fname2 = dateiname.split("\\.")[1];
                    dateiname = fname1 + "1." + fname2;
                }
                else
                    dateiname = dateiname +"1";
            }
            File fp = new File(dateiname);

            String str_srcsize = "";
            for(int z = 0; z < 8; z++)
                str_srcsize += String.format("%8s", Integer.toBinaryString(receiveData[8+z] & 0xFF)).replace(' ', '0');
                
            srcsize = new BigInteger(str_srcsize, 2);

            BigInteger bInt_1497 = new BigInteger("1497");
            n_packets =  (srcsize.divide(bInt_1497)).longValue();

            //add 4 wegen crc am ende
            if(srcsize.mod(bInt_1497) != new BigInteger("0"))   //Falls Paketgröße kein Vielfaches von 1500 ist
                n_packets ++;


            if(debuginfo)
                System.out.println("Anzahl Pakete: " + n_packets);
        //System.out.println("(srcsize.add(new BigInteger(\"4\", 2): " + (srcsize.add(new BigInteger("4", 2))));
            
            //32-Bit-CRC Check
            // update the current checksum with the specified array of bytes
            checksum.update(receiveData, 0, 18 + l); //funktioniert
            System.out.println("CRC Data StartPacket: " + checksum.getValue());
                
           
            
            for(int k = 18+l; k < 18+l+4; k++){
                str_validChecksum += String.format("%8s", Long.toBinaryString(receiveData[k] & 0xFF)).replace(' ', '0');
            }
            
            if(Long.parseLong(str_validChecksum,2) == checksum.getValue()) //Wenn CRC erfolgreich :
            {   
                System.out.println("CRC Check erfolgreich");
            //Bestätigungspakete (Server -> Client)
            //16-Bit-Sessionnummer
                sendData[0] = receiveData[0];
                sendData[1] = receiveData[1];
                sendData[2] = (byte)(0); //ACK  //erstes Datenpaket soll ACK 1 sein
                cnt++;
                sendPacket = new DatagramPacket(sendData, 3/*sendData.length*/, IPAddress, port);
                serverSocket.send(sendPacket);
            }
            else
                System.out.println("CRC Check fehlgeschlagen");

        FileOutputStream filestream = new FileOutputStream(dateiname);

      //==================================================================================================================================
        int lastpacketsize = (srcsize.mod(new BigInteger("1497"))).intValue();
        int secondlastsize = 1497;

        if(lastpacketsize > 1493) {
            n_packets++;
            secondlastsize = 1493;
            lastpacketsize = lastpacketsize - secondlastsize;
        }


        int lastAck = 0;

      while(cnt < n_packets+1) {

          if(debuginfo)
            System.out.println("lastAck : " + lastAck);

          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);


          //Verzögerung vom Client simulieren:
          if (delayTime > 0) {
              TimeUnit.MILLISECONDS.sleep(delayTime);
              delayTime = delay_input + (new Random().nextInt() % (delay_input / 10)); //sinnvolle abweichung wählen
          }

          serverSocket.receive(receivePacket);

          if (args.length > 1 && (new Random().nextInt() % timesToLoss) == 0) //-> Paket verwerfen
          {    //Debughilfe:
              if (debuginfo)
                  System.out.println("\nClientpaket zufälliger Weise verworfen\n");
              continue;
          }



          //if last packet
          if (cnt == n_packets -1 || cnt == n_packets) {
              if (debuginfo)
                  System.out.println("Last two packets");

              //ACK
              sendData[2] = (byte) lastAck;

              if(receiveData[0] == sessionnr[0] && receiveData[1] == sessionnr[1]) {
                  if (receiveData[2] == (lastAck + 1) % 2) {

                      lastAck = receiveData[2];

                      int packetsize = secondlastsize;

                      if (cnt == n_packets) {
                          packetsize = lastpacketsize;
                      }

                      if (debuginfo)
                          System.out.println("packetsize(last two packets): " + packetsize);

                      //ACK
                      sendData[2] = (byte) lastAck;
                      cnt++;

                      byte[] bytesToFile = new byte[packetsize];

                      System.arraycopy(receiveData,
                              3,
                              bytesToFile,
                              0,
                              packetsize);

                      filestream.write(bytesToFile);

                      if (cnt == n_packets + 1) {
                          //CRC über gesamtdatei
                          Checksum filechecksum = new CRC32();// Polynom: 0x04C11DB7
                          // update the current checksum with the specified array of bytes

                          File in_file = new File(dateiname);
                          byte[] filebytes = Files.readAllBytes(in_file.toPath());

                          filechecksum.update(filebytes, 0, filebytes.length); //funktioniert

                          String c1 = String.format("%8s", Integer.toBinaryString(receiveData[lastpacketsize + 3] & 0xFF)).replace(' ', '0');
                          String c2 = String.format("%8s", Integer.toBinaryString(receiveData[lastpacketsize + 4] & 0xFF)).replace(' ', '0');
                          String c3 = String.format("%8s", Integer.toBinaryString(receiveData[lastpacketsize + 5] & 0xFF)).replace(' ', '0');
                          String c4 = String.format("%8s", Integer.toBinaryString(receiveData[lastpacketsize + 6] & 0xFF)).replace(' ', '0');
                          BigInteger crc_overall = new BigInteger(c1 + c2 + c3 + c4, 2);
                          System.out.println("CRC über Gesamtdatei: " + filechecksum.getValue());
                          if (filechecksum.getValue() == crc_overall.longValue()) {
                              System.out.println("CRC über Gesamtdatei erfolgreich");
                              System.out.println("<" +
                                      dateiname + " gespeichert> [" + filebytes.length + "]"
                              );
                          } else
                              System.out.println("CRC über Gesamtdatei fehlgeschlagen");
                      }

                  }
              }
              //ACK senden
              //====================================
              //16-Bit-Sessionnummer
              sendData[0] = sessionnr[0];
              sendData[1] = sessionnr[1];

              sendPacket = new DatagramPacket(sendData, 3, IPAddress, port);


          }
          else {



              //ACK
              sendData[2] = (byte) lastAck; //1.Erstes Paket soll ACK 1 sein

              byte[] bytesToFile = new byte[1497];
              if (receiveData[2] == (lastAck+1) % 2) {
                  lastAck = receiveData[2];
                  sendData[2] = (byte) (lastAck);
                  cnt++;

                  System.arraycopy(receiveData,
                          3,
                          bytesToFile,
                          0,
                          receiveData.length - 3);
               if(debuginfo)
                  System.out.println("cnt_packets: " + cnt);

                  //Write to file
                  filestream.write(bytesToFile);


              }
              //ACK senden
              //====================================
              //16-Bit-Sessionnummer
              sendData[0] = receiveData[0];
              sendData[1] = receiveData[1];


              sendPacket = new DatagramPacket(sendData, 3, IPAddress, port);


              //====================================
          }


          if (args.length > 1 && (new Random().nextInt() % timesToLoss) == 0) {
              //Debughilfe:
              if (debuginfo)
                  System.out.println("\nServerpaket zufälliger Weise verworfen\n" + "TImestoLoss: " + timesToLoss);
          }
          else {
              //Verzögerung vom Server simulieren:
              if (delayTime > 0) {
                  TimeUnit.MILLISECONDS.sleep(delayTime);
                  delayTime = delay_input + (new Random().nextInt() % (delay_input / 10)); //sinnvolle abweichung wählen
              }
              serverSocket.send(sendPacket);
          }
      }
                
    } 
}