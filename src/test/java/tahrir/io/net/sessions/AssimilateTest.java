package tahrir.io.net.sessions;

import java.io.File;
import java.util.SortedSet;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import org.testng.Assert;
import org.testng.annotations.Test;

import tahrir.*;
import tahrir.io.crypto.TrCrypto;
import tahrir.io.net.*;
import tahrir.io.net.broadcasts.UserIdentity;
import tahrir.io.net.broadcasts.broadcastMessages.BroadcastMessage;
import tahrir.io.net.broadcasts.containers.BroadcastMessageInbox;
import tahrir.tools.*;
import tahrir.tools.TrUtils.TestUtils;
import tahrir.ui.TrMainWindow;

public class AssimilateTest {
	@Test
	public void twoPeerTest() throws Exception {
		System.out.println("Joiner (7644) will assimilate to seed (7643)");

		final TrNodeConfig seedConfig = new TrNodeConfig();
		seedConfig.capabilities.allowsAssimilation = true;
		seedConfig.capabilities.allowsUnsolicitiedInbound = true;
		seedConfig.peers.runMaintainance = false;
		seedConfig.peers.runBroadcast = false;
		seedConfig.localHostName = "localhost";
		seedConfig.udp.listenPort = 7643;
		final File seedDir = TestUtils.createTempDirectory();
		final TrNode seedNode = new TrNode(seedDir, seedConfig);
		final RemoteNodeAddress seedPublicNodeId = seedNode.getRemoteNodeAddress();

		final File joinerDir = TestUtils.createTempDirectory();

		final TrNodeConfig joinerConfig = new TrNodeConfig();

		joinerConfig.udp.listenPort = 7644;
		joinerConfig.localHostName = "localhost";
		joinerConfig.peers.runMaintainance = true;
		joinerConfig.peers.topologyMaintenance = false;
		joinerConfig.peers.runBroadcast = false;

		final File joinerPubNodeIdsDir = new File(joinerDir, joinerConfig.publicNodeIdsDir);

		joinerPubNodeIdsDir.mkdir();

		// Ok, we should be getting this TrPeerInfo out of seedNode somehow rather
		// than needing to set its capabilities manually like this
		final TrPeerManager.TrPeerInfo seedPeerInfo = new TrPeerManager.TrPeerInfo(seedPublicNodeId);
		seedPeerInfo.capabilities = seedConfig.capabilities;

		Persistence.save(new File(joinerPubNodeIdsDir, "joiner-id"), seedPeerInfo);

		final TrNode joinerNode = new TrNode(joinerDir, joinerConfig);

		for (int x=0; x<50; x++) {
			Thread.sleep(200);
			if (joinerNode.peerManager.peers.containsKey(seedNode.getRemoteNodeAddress().physicalLocation)
					&& seedNode.peerManager.peers.containsKey(joinerNode.getRemoteNodeAddress().physicalLocation)) {
				break;
			}
		}
		// Verify that they are now connected
		Assert.assertTrue(joinerNode.peerManager.peers.containsKey(seedNode.getRemoteNodeAddress().physicalLocation), "The joiner peer manager should contain the seed peer");
		Assert.assertTrue(seedNode.peerManager.peers.containsKey(joinerNode.getRemoteNodeAddress().physicalLocation), "The seed peer manager should contain the joiner peer");
	}

    @Test
    public void threePeerTest() throws Exception{
        final TrNodeConfig seedConfig = new TrNodeConfig();
        seedConfig.capabilities.allowsAssimilation = true;
        seedConfig.capabilities.allowsUnsolicitiedInbound = true;
        seedConfig.peers.runMaintainance = false;
        seedConfig.peers.runBroadcast = false;
        seedConfig.localHostName = "localhost";
        seedConfig.udp.listenPort = 7643;
        final File seedDir = TestUtils.createTempDirectory();
        final TrNode seedNode = new TrNode(seedDir, seedConfig);
        final RemoteNodeAddress seedPublicNodeId = seedNode.getRemoteNodeAddress();

        final File joinerDir = TestUtils.createTempDirectory();

        final TrNodeConfig joinerConfig = new TrNodeConfig();

        joinerConfig.udp.listenPort = 7644;
        joinerConfig.localHostName = "localhost";
        joinerConfig.peers.runMaintainance = true;
        joinerConfig.peers.topologyMaintenance = false;
        joinerConfig.peers.runBroadcast = true;

        final File joinerPubNodeIdsDir = new File(joinerDir, joinerConfig.publicNodeIdsDir);

        joinerPubNodeIdsDir.mkdir();

        final File joinerDir2 = TestUtils.createTempDirectory();

        final TrNodeConfig joinerConfig2 = new TrNodeConfig();

        joinerConfig2.udp.listenPort = 7645;
        joinerConfig2.localHostName = "localhost";
        joinerConfig2.peers.runMaintainance = true;
        joinerConfig2.peers.topologyMaintenance = false;
        joinerConfig2.peers.runBroadcast = true;

        final File joinerPubNodeIdsDir2 = new File(joinerDir2, joinerConfig2.publicNodeIdsDir);

        joinerPubNodeIdsDir2.mkdir();


        final TrPeerManager.TrPeerInfo seedPeerInfo = new TrPeerManager.TrPeerInfo(seedPublicNodeId);
        seedPeerInfo.capabilities = seedConfig.capabilities;

        Persistence.save(new File(joinerPubNodeIdsDir, "joiner-id"), seedPeerInfo);
        Persistence.save(new File(joinerPubNodeIdsDir2, "joiner-id2"), seedPeerInfo);
        final TrNode joinerNode = new TrNode(joinerDir, joinerConfig);
        final TrNode joinerNode2 = new TrNode(joinerDir2, joinerConfig2);

        try{
            final TrMainWindow mainWindow = new TrMainWindow(joinerNode);
            mainWindow.getContent().revalidate();

            final TrMainWindow mainWindow2 = new TrMainWindow(joinerNode2);
            mainWindow2.getContent().revalidate();
        }
        catch (final Exception e){
            e.printStackTrace();
        }
        Thread.sleep(50000);

    }
}
