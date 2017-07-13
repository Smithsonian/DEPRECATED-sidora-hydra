package edu.si.sidora.hydra;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface TransmissionService {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/transmit")
    public Response transmitToHydra(@FormParam("pid") String pid, @FormParam("user") String user
                    // the param below wold be used to engage "manual" location selection
                    //, @FormParam("location") String location
                    );

}
