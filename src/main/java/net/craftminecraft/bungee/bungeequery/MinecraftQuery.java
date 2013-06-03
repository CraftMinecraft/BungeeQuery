/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.craftminecraft.bungee.bungeequery;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.LittleEndianDataOutputStream;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;

/**
 *
 * @author roblabla
 */
public class MinecraftQuery implements Runnable {
    private BungeeQuery plugin;
    private Map<Integer,Integer> tokens;
    private ListenerInfo info;
    private ScheduledTask task;
    private final Runnable runnabletask = new Runnable() {
        public void run() {
            MinecraftQuery.this.tokens.clear();
            MinecraftQuery.this.task = null;
        }
    };

    public MinecraftQuery(BungeeQuery plugin, ListenerInfo info) {
        this.tokens = Maps.newConcurrentMap();
        this.plugin = plugin;
        this.info = info;
    }

    public void run() {
        try {
            DatagramSocket serverSocket = new DatagramSocket(25565);
            byte[] receiveData = new byte[1460];
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                if (receiveData[0] != 0xFE && receiveData[1] != 0xFD) {
                    plugin.getLogger().log(Level.INFO, "Invalid packet [" + receivePacket.getAddress() + "]");
                    plugin.getLogger().log(Level.INFO, DatatypeConverter.printHexBinary(receiveData));
                    continue;
                }
                plugin.getLogger().log(Level.INFO, "Received packet from " + receivePacket.getAddress() + "]");
                if (receiveData[2] == 9) {
                    // Handshake
                    final int sessionId = Ints.fromByteArray(Arrays.copyOfRange(receiveData, 3, 7));
                    int token = new Random().nextInt();
                    tokens.put(sessionId, token);

                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.write(9);
                    out.write(sessionId);
                    out.write((Integer.toString(token) + "\0").getBytes(Charsets.UTF_8));
                    serverSocket.send(new DatagramPacket(out.toByteArray(), out.toByteArray().length, receivePacket.getSocketAddress()));
                    
                    if (task == null) {
                        task = this.plugin.getProxy().getScheduler().schedule(plugin, runnabletask, 30, TimeUnit.SECONDS);
                    }

                } else if (receiveData[2] == 0) {
                    final int sessionId = Ints.fromByteArray(Arrays.copyOfRange(receiveData, 3, 7));
                    final int challenge = Ints.fromByteArray(Arrays.copyOfRange(receiveData, 7, 11));
                    if (challenge != tokens.get(sessionId)) {
                        plugin.getLogger().log(Level.INFO, "Invalid challenge [" + receivePacket.getAddress() + "]");
                        plugin.getLogger().log(Level.INFO, DatatypeConverter.printHexBinary(receiveData));
                        continue;
                    }

                    if (receiveData.length > 11) {
                        byte[] out = shortAnswer(sessionId);
                        DatagramPacket packet = new DatagramPacket(out, out.length, receivePacket.getSocketAddress());
                        serverSocket.send(packet);
                    } else {
                        byte[] out = shortAnswer(sessionId);
                        DatagramPacket packet = new DatagramPacket(out, out.length, receivePacket.getSocketAddress());
                        serverSocket.send(packet);
                    }
                }
            }

        } catch (SocketException ex) {
            plugin.getLogger().log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }
    }
    
    public byte[] shortAnswer(final int sessionId) {
        //plugin.getProxy().getConfigurationAdapter().getListeners()
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.write(0);                                      // type

        out.write(sessionId);                              // sessionId
        
        String motd = info.getMotd() + "\0";
        out.write(motd.getBytes(Charsets.UTF_8));          // motd
        
        out.write("SMP\0".getBytes(Charsets.UTF_8));       // Gametype
        
        out.write("world\0".getBytes(Charsets.UTF_8));     // map
        
        String playercount = "" + plugin.getProxy().getPlayers().size() + "\0";
        out.write(playercount.getBytes(Charsets.UTF_8));   // player count
        
        String maxplayers = info.getMaxPlayers()+ "\0";
        out.write(maxplayers.getBytes(Charsets.UTF_8));    // max players
        
        ByteBuffer port = ByteBuffer.allocate(2);
        port.order(ByteOrder.LITTLE_ENDIAN);
        port.putShort((short) info.getHost().getPort());
        port.hasArray();
        out.write(port.array());                           // host port
        
        String hostip = info.getHost().getAddress().getHostAddress() + "\0";
        out.write(hostip.getBytes(Charsets.UTF_8));        // host IP
        return out.toByteArray();
    }
    
    public byte[] fullAnswer(final int sessionId) {
        //plugin.getProxy().getConfigurationAdapter().getListeners()
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.write(0);                                      // type
        out.write(sessionId);                              // sessionId
        byte[] padding = new byte[] {
          (byte)0x73, (byte)0x70, (byte)0x6C, (byte)0x69, (byte)0x74, 
          (byte)0x6E, (byte)0x75, (byte)0x6D, (byte)0x00, (byte)0x80, (byte)0x00	
        };
        out.write(padding);                                // padding
        
        StringBuilder kvpair = new StringBuilder();
        kvpair.append("hostname\0");
        kvpair.append(info.getMotd() + "\0");
        kvpair.append("gametype\0");
        kvpair.append("SMP\0");
        kvpair.append("game_id\0");
        kvpair.append("MINECRAFT\0");
        kvpair.append("version\0");
        kvpair.append(plugin.getProxy().getGameVersion() + "\0");
        kvpair.append("plugins\0");
        kvpair.append(plugin.getProxy().getName() + " ");
        kvpair.append(plugin.getProxy().getVersion() + ":");
        for (Plugin p : plugin.getProxy().getPluginManager().getPlugins()) {
            kvpair.append(p.getDescription().getName() + " ");
            kvpair.append(p.getDescription().getVersion() + ";");
        }
        kvpair.deleteCharAt(kvpair.length());
        kvpair.append("\0");
        kvpair.append("map\0");
        kvpair.append("world\0");
        kvpair.append("numplayers\0");
        kvpair.append(plugin.getProxy().getPlayers().size() + "\0");
        kvpair.append("maxplayers\0");
        kvpair.append(info.getMaxPlayers() + "\0");
        kvpair.append("hostport\0");
        kvpair.append(info.getHost().getPort() + "\0");
        kvpair.append("hostip\0");
        kvpair.append(info.getHost().getAddress().getHostAddress() + "\0");
        kvpair.append("\0"); // done
        out.write(kvpair.toString().getBytes(Charsets.UTF_8));

        padding = new byte[] {
          (byte)0x01, (byte)0x70, (byte)0x6C, (byte)0x61, (byte)0x79, 
          (byte)0x65, (byte)0x72, (byte)0x5F, (byte)0x00, (byte)0x00
        };
        out.write(padding);                               // padding
        
        StringBuffer buff = new StringBuffer();
        for (ProxiedPlayer p : plugin.getProxy().getPlayers()) {
            buff.append(p.getName() + "\0");
        }
        buff.append("\0");
        out.write(buff.toString().getBytes(Charsets.UTF_8));   // players
        
        return out.toByteArray();
    }
}
