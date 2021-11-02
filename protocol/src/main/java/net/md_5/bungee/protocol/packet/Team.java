package net.md_5.bungee.protocol.packet;

import net.md_5.bungee.protocol.DefinedPacket;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.md_5.bungee.protocol.AbstractPacketHandler;
import net.md_5.bungee.protocol.ProtocolConstants;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Team extends DefinedPacket
{

    private String name;
    /**
     * 0 - create, 1 remove, 2 info update, 3 player add, 4 player remove.
     */
    private byte mode;
    private String displayName;
    private String prefix;
    private String suffix;
    private String nameTagVisibility;
    private byte color;
    private byte friendlyFire;
    private String[] players;

    /**
     * Packet to destroy a team.
     */
    public Team(String name)
    {
        this();
        this.name = name;
        this.mode = 1;
    }

    @Override
    public void read(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        name = readString( buf );
        mode = buf.readByte();
        if ( mode == 0 || mode == 2 )
        {
            displayName = readString( buf );
            prefix = readString( buf );
            suffix = readString( buf );
            friendlyFire = buf.readByte();
            if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_8 )
            {
                nameTagVisibility = readString( buf );
                color = buf.readByte();
            }
        }
        if ( mode == 0 || mode == 3 || mode == 4 )
        {
            int len = ( protocolVersion >= ProtocolConstants.MINECRAFT_1_8 ) ? readVarInt( buf ) : buf.readShort();
            players = new String[ len ];
            for ( int i = 0; i < len; i++ )
            {
                players[i] = readString( buf );
            }
        }
    }

    @Override
    public void write(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        writeString( name, buf );
        buf.writeByte( mode );
        if ( mode == 0 || mode == 2 )
        {
            writeString( displayName, buf );
            writeString( prefix, buf );
            writeString( suffix, buf );
            buf.writeByte( friendlyFire );
            if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_8 )
            {
                writeString( nameTagVisibility, buf );
                buf.writeByte( color );
            }
        }
        if ( mode == 0 || mode == 3 || mode == 4 )
        {
            if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_8 )
            {
                writeVarInt( players.length, buf );
            } else
            {
                buf.writeShort( players.length );
            }
            for ( String player : players )
            {
                writeString( player, buf );
            }
        }
    }

    @Override
    public void handle(AbstractPacketHandler handler) throws Exception
    {
        handler.handle( this );
    }
}
