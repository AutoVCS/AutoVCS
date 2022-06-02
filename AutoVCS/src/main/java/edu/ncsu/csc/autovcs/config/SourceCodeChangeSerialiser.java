package edu.ncsu.csc.autovcs.config;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;

/**
 * This is used to help write out the JSON for the API endpoints
 * 
 * @author Kai Presler-Marshall
 *
 */
public class SourceCodeChangeSerialiser extends StdSerializer<SourceCodeChange> {

    private static final long serialVersionUID = -8088227699801387899L;

    public SourceCodeChangeSerialiser () {
        this( null );
    }

    public SourceCodeChangeSerialiser ( final Class<SourceCodeChange> t ) {
        super( t );
    }

    @Override
    public void serialize ( final SourceCodeChange scc, final JsonGenerator jsonGenerator,
            final SerializerProvider serializer ) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField( "change", scc.toString() );
        jsonGenerator.writeEndObject();
    }

}
