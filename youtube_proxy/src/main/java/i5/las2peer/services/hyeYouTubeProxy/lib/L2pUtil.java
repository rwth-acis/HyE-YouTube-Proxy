package i5.las2peer.services.hyeYouTubeProxy.lib;

import i5.las2peer.api.Context;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.logging.L2pLogger;

import java.io.Serializable;

public abstract class L2pUtil {


    /**
     * Retrieve a user's YouTube ID
     *
     * @param user The User Agent whose YouTube ID we are interested in
     * @return The YouTube ID linked to the given user
     */
    public static String getUserId(UserAgent user) {
        if (user == null)
            return "";
        return user.getIdentifier();
    }

    /**
     * Fetches the user associated with the given handle
     *
     * @param context The current context from which the agent is fetched
     * @param handle The current las2peer ID of the user in question
     * @return The requested las2peer user agent or null if there was an issue
     */
    public static UserAgent getUserAgent(Context context, String handle, L2pLogger log) {
        UserAgent user;
        try {
            user = (UserAgent) context.fetchAgent(handle);
        } catch (Exception e) {
            if (log != null)
                log.printStackTrace(e);
            return null;
        }
        return user;
    }

    /**
     * Helper function which either fetches or creates an envelope with the given handle
     *
     * @param context The current execution context required to access the user's local storage
     * @param handle The handle associated with the envelope in question
     * @return The requested las2peer Envelope
     */
    public static Envelope getEnvelope(Context context, String handle, Agent owner, L2pLogger log) {
        Envelope env;

        // See whether envelope already exists
        try {
            if (owner == null)
                env = context.requestEnvelope(handle);
            else
                env = context.requestEnvelope(handle, owner);
        } catch (EnvelopeNotFoundException e) {
            // Envelope does not exist
            env = null;
        } catch (Exception e) {
            if (log != null)
                log.printStackTrace(e);
            return null;
        }

        // Else create envelope
        if (env == null) {
            try {
                if (owner == null)
                    env = context.createEnvelope(handle);
                else
                    env = context.createEnvelope(handle, owner);
            } catch (Exception e) {
                if (log != null)
                    log.printStackTrace(e);
                return null;
            }
        }

        return env;
    }

    /**
     * Helper function which either fetches or creates an envelope with the given handle and given content
     *
     * @param context The current execution context required to access the user's local storage
     * @param handle The handle associated with the envelope in question
     * @param content The content to be stored
     * @param owner If not null, the envelope is signed with the given owners private key
     * @return Whether envelope was successfully stored
     */
    public static boolean storeEnvelope(Context context, String handle, Serializable content, Agent owner, L2pLogger log) {
        Envelope env;
        env = getEnvelope(context, handle, owner, log);

        // Store content
        try {
            env.setContent(content);
            if (owner == null)
                context.storeEnvelope(env);
            else
                context.storeEnvelope(env, owner);
        } catch (Exception e) {
            if (log != null)
                log.printStackTrace(e);
            return false;
        }
        return true;
    }
}
