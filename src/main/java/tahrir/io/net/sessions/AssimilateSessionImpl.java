package tahrir.io.net.sessions;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tahrir.TrNode;
import tahrir.io.net.*;
import tahrir.io.net.TrPeerManager.Capabilities;
import tahrir.io.net.TrPeerManager.TrPeerInfo;
import tahrir.tools.Persistence.Modified;
import tahrir.tools.Persistence.ModifyBlock;
import tahrir.tools.TrUtils;

import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AssimilateSessionImpl extends TrSessionImpl implements AssimilateSession {

    private final Logger logger;

    public static final long RELAY_ASSIMILATION_TIMEOUT_SECONDS = 60;
    private boolean locallyInitiated;
    private AssimilateSession pubNodeSession;
    private AssimilateSession receivedRequestFrom;
    private PhysicalNetworkLocation acceptorPhysicalLocation, joinerPhysicalLocation;
    private RSAPublicKey acceptorPubkey;
    private long requestNewConnectionTime;
    private ScheduledFuture<?> requestNewConnectionFuture;
    private TrPeerInfo relay;
    private Capabilities acceptorCapabilities;
    private int acceptorLocation;
    private RSAPublicKey joinerPublicKey;
    private Set<PhysicalNetworkLocation> alreadyAttempted=new ConcurrentSkipListSet<PhysicalNetworkLocation>();

    public AssimilateSessionImpl(final Integer sessionId, final TrNode node, final TrSessionManager sessionMgr) {
        super(sessionId, node, sessionMgr);
        logger = LoggerFactory.getLogger(AssimilateSessionImpl.class.getName() + " [sesId: " + sessionId + "]");
    }

    public void startAssimilation(final Runnable onFailure, final TrPeerInfo assimilateVia) {
        logger.debug("Start assimilation via " + assimilateVia);
        relay = assimilateVia;
        requestNewConnectionTime = System.currentTimeMillis();
        requestNewConnectionFuture = TrUtils.executor.schedule(new AssimilationFailureChecker(), RELAY_ASSIMILATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        locallyInitiated = true;
        pubNodeSession = this.remoteSession(AssimilateSession.class, this.connection(assimilateVia.remoteNodeAddress, true));
        pubNodeSession.registerFailureListener(onFailure);
        pubNodeSession.requestNewConnection(node.getRemoteNodeAddress().publicKey);
    }

    public void yourAddressIs(final PhysicalNetworkLocation address) {
        if (!locallyInitiated) {
            logger.warn("Received yourAddressIs() from {}, yet this AssimilateSession was not locally initiated",
                    sender());
            return;
        }
        if (!sender().equals(relay.remoteNodeAddress.physicalLocation)) {
            logger.warn("Received yourAddressIs() from {}, yet the public node we expected it from was {}, ignoring",
                    sender(), relay.remoteNodeAddress.physicalLocation);

            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug(sender() + " told us that our external address is " + address);
        }
        node.modifyPublicNodeId(new ModifyBlock<RemoteNodeAddress>() {

            public void run(final RemoteNodeAddress object, final Modified modified) {
                object.physicalLocation = address;
            }
        });
    }

    public int generateUId() {
        return TrUtils.rand.nextInt();
    }

    public void requestNewConnection(final RSAPublicKey requestorPubkey) {
        requestNewConnection(new RemoteNodeAddress(sender(), requestorPubkey), generateUId());
    }

    public void requestNewConnection(final RemoteNodeAddress joinerAddress, int uId) {
        joinerPhysicalLocation = joinerAddress.physicalLocation;
        joinerPublicKey = joinerAddress.publicKey;
        final PhysicalNetworkLocation senderFV = sender();

        if (locallyInitiated) {
            logger.warn("Received requestNewConnection() from {}, but the session was locally initiated, ignoring",
                    senderFV);
            return;
        }
        if (receivedRequestFrom != null) {
            logger.warn("Recieved another requestNewConnection() from {}, ignoring", senderFV);
            return;
        }
        receivedRequestFrom = this.remoteSession(AssimilateSession.class, connection(senderFV));

        Optional<DateTime> uIdSeenBeforeTime = Optional.fromNullable(node.peerManager.seenUID.getIfPresent(uId));
        if (uIdSeenBeforeTime.isPresent()) {
            logger.debug("Request already occurred at " + uIdSeenBeforeTime.get() + ", going to reject it to prevent loops");
            receivedRequestFrom.rejectAlreadySeen(uId);
        }
        else {
            node.peerManager.seenUID.asMap().put(uId, new DateTime());
            logger.debug("New request. Added to cache");

            if (joinerPhysicalLocation == null) {
                receivedRequestFrom.yourAddressIs(senderFV);
                joinerPhysicalLocation = senderFV;
            }
            if ((node.peerManager.peers.size() < node.peerManager.config.maxPeers) && !node.peerManager.peers.containsKey(joinerPhysicalLocation)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Accepting joiner {} as a peer", joinerAddress);
                }
                // We're going to accept them
                final RemoteNodeAddress remoteNodeAddress = node.getRemoteNodeAddress();
                receivedRequestFrom.acceptNewConnection(remoteNodeAddress);
                final AssimilateSession requestorSession = remoteSession(AssimilateSession.class,
                        connectionWithUserLabel(joinerAddress, false, "topology"));
                requestorSession.myCapabilitiesAre(node.config.capabilities, node.peerManager.locInfo.getLocation());
            } else relayAssimilateRequest(joinerAddress);
        }
    }

    private void relayAssimilateRequest(RemoteNodeAddress joinerAddress) {
        relay = node.peerManager.getPeerForAssimilation(alreadyAttempted);
        if (logger.isDebugEnabled()) {
            logger.debug("Forwarding assimilation request from {} to {}", joinerAddress, relay);
        }

        requestNewConnectionTime = System.currentTimeMillis();
        requestNewConnectionFuture = TrUtils.executor.schedule(new AssimilationFailureChecker(), RELAY_ASSIMILATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        final AssimilateSession relaySession = remoteSession(AssimilateSession.class, connection(relay));

        // A hack so that we can pass this into the Runnable callback
        final PhysicalNetworkLocation finalRequestorPhysicalAddress = joinerPhysicalLocation;

        relaySession.registerFailureListener(new Runnable() {

            public void run() {
                if (logger.isDebugEnabled()) {
                    logger.debug("Reporting assimilation failure to peerManager after sending assimilation request to {}, and then trying again", relay);
                }
                node.peerManager.reportAssimilationFailure(relay.remoteNodeAddress.physicalLocation);
                // Note: Important to use requestAddress field rather than
                // the parameter because the parameter may be null
                AssimilateSessionImpl.this.requestNewConnection(new RemoteNodeAddress(finalRequestorPhysicalAddress, joinerPublicKey), generateUId());
            }
        });

        relaySession.requestNewConnection(new RemoteNodeAddress(joinerPhysicalLocation, joinerPublicKey), generateUId());
    }

    public void rejectAlreadySeen(int uId){
        alreadyAttempted.add(sender());
        relayAssimilateRequest(new RemoteNodeAddress(joinerPhysicalLocation, joinerPublicKey));
    }

    public void acceptNewConnection(final RemoteNodeAddress acceptorAddress) {
        final PhysicalNetworkLocation acceptorPhysicalLocation = acceptorAddress.physicalLocation;
        final RSAPublicKey acceptorPubkey = acceptorAddress.publicKey;

        if (!sender().equals(relay.remoteNodeAddress.physicalLocation)) {
            logger.warn("Received acceptNewConnection() from {}, but was expecting it from {}", sender(), relay.remoteNodeAddress.physicalLocation);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("{} is accepting assimiliation request", acceptorPhysicalLocation);
        }
        requestNewConnectionFuture.cancel(false);
        node.peerManager.updatePeerInfo(relay.remoteNodeAddress.physicalLocation, new Function<TrPeerManager.TrPeerInfo, Void>() {

            public Void apply(final TrPeerInfo tpi) {
                node.peerManager.reportAssimilationSuccess(relay.remoteNodeAddress.physicalLocation, System.currentTimeMillis()
                        - requestNewConnectionTime);
                return null;
            }
        });

        if (!locallyInitiated) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} is accepting assimilation request, forwarding acceptance back to {}", joinerPhysicalLocation);
            }
            receivedRequestFrom.acceptNewConnection(acceptorAddress);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("{} accepted our assimiliation request, add a new connection to them", acceptorPhysicalLocation);
            }

            // We now need to allow other methods to use the acceptor information
            this.acceptorPhysicalLocation = acceptorPhysicalLocation;
            this.acceptorPubkey = acceptorPubkey;

            logger.debug("Connect to {} and inform them of our capabilities", acceptorPhysicalLocation);
            final AssimilateSession acceptorSession = remoteSession(AssimilateSession.class,
                    connectionWithUserLabel(acceptorAddress, false, "topology"));
            acceptorSession.myCapabilitiesAre(node.config.capabilities, node.peerManager.locInfo.getLocation());

            if (acceptorCapabilities != null) {
                // If we've already received the myCapabilitiesAre from the acceptor
                // then we can now add it to our peer manager
                logger.debug("Adding new connection to acceptor {}", acceptorPhysicalLocation);
                node.peerManager.addNewPeer(new RemoteNodeAddress(acceptorPhysicalLocation,
                        acceptorPubkey), acceptorCapabilities, acceptorLocation);
            }
        }
    }

    public void myCapabilitiesAre(final Capabilities myCapabilities, final int topologyLocation) {
        if (locallyInitiated) {
            if (acceptorPhysicalLocation != null && !sender().equals(acceptorPhysicalLocation)) {
                logger.error("Received myCapabiltiesAre but not from acceptor, ignoring");
                return;
            }
            acceptorCapabilities = myCapabilities;
            acceptorLocation = topologyLocation;
            if (acceptorPhysicalLocation != null && acceptorPubkey != null) {
                // If we've already received the acceptorAddress and acceptorPubkey from an
                // acceptNewConnection message from the acceptor then we can now add it to
                // our peer manager
                logger.debug("Adding new connection to acceptor {}", acceptorPhysicalLocation);
                node.peerManager.addNewPeer(new RemoteNodeAddress(acceptorPhysicalLocation,
                        acceptorPubkey), myCapabilities, topologyLocation);
            }
        } else {
            if (!sender().equals(joinerPhysicalLocation)) {
                logger.error("Received myCapabiltiesAre from " + sender() + ", but not from the joiner " + joinerPhysicalLocation + ", ignoring");
                return;
            }
            node.peerManager.addNewPeer(new RemoteNodeAddress(joinerPhysicalLocation,
                    joinerPublicKey), myCapabilities, topologyLocation);
        }
    }

    private class AssimilationFailureChecker implements Runnable {
        public void run() {
            node.peerManager.updatePeerInfo(relay.remoteNodeAddress.physicalLocation, new Function<TrPeerManager.TrPeerInfo, Void>() {

                public Void apply(final TrPeerInfo tpi) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Reporting assimilation failure to peerManager after sending assimilation request to {}", relay);
                    }
                    node.peerManager.reportAssimilationFailure(relay.remoteNodeAddress.physicalLocation);
                    return null;
                }
            });
        }
    }
}