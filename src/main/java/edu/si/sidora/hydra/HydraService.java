package edu.si.sidora.hydra;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface HydraService {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/transmit")
    public Response transmitToHydra(@FormParam("pid") String pid, @FormParam("user") String user,
                    @FormParam("email") String email);
    

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/receive")
    public Response receiveFromDropbox(@FormParam("pid") String pid, @FormParam("user") String user,
                    @FormParam("email") String email);
    
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/list")
    public Response listDropbox(@FormParam("user") String user);


}
