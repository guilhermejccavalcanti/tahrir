package tahrir.io.net.sessions;

import java.io.*;
import java.util.List;

import tahrir.*;
import tahrir.io.net.*;
import tahrir.io.net.TrPeerManager.TrPeerInfo;
import tahrir.tools.*;

import com.google.common.collect.Lists;

public class LongTests {
	public static void main(final String[] args) throws Exception {
		final LongTests test = new LongTests();
		test.start();
	}

	public void start() throws Exception {
		final TrConfig seedConfig = new TrConfig();
		seedConfig.capabilities.allowsAssimilation = true;
		seedConfig.capabilities.allowsUnsolicitiedInbound = true;
		seedConfig.peers.assimilate = false;
		seedConfig.peers.runMaintainance = false;
		seedConfig.peers.maxPeers = 5;
		seedConfig.peers.minPeers = 2;
		seedConfig.localHostName = "localhost";
		seedConfig.udp.listenPort = 10648;
		final File seedDir = TrUtils.createTempDirectory();
		final TrNode seedNode = new TrNode(seedDir, seedConfig);
		final RemoteNodeAddress seedPublicNodeId = seedNode.getRemoteNodeAddress();

		final List<TrNode> joiners = Lists.newLinkedList();

		for (int x=0; x<100; x++) {
			Thread.sleep(500);
			final File joinerDir = TrUtils.createTempDirectory();

			final TrConfig joinerConfig = new TrConfig();

			joinerConfig.udp.listenPort = 16050+x;
			joinerConfig.localHostName = "localhost";
			joinerConfig.peers.assimilate = true;
			joinerConfig.peers.runMaintainance = true;
			joinerConfig.peers.maxPeers = 5;
			joinerConfig.peers.minPeers = 2;
			final File joinerPubNodeIdsDir = new File(joinerDir, joinerConfig.publicNodeIdsDir);

			joinerPubNodeIdsDir.mkdir();

			// Ok, we should be getting this TrPeerInfo out of seedNode somehow rather
			// than needing to set its capabilities manually like this
			final TrPeerManager.TrPeerInfo seedPeerInfo = new TrPeerManager.TrPeerInfo(seedPublicNodeId);
			seedPeerInfo.capabilities = seedConfig.capabilities;

			Persistence.save(new File(joinerPubNodeIdsDir, "joiner-id"), seedPeerInfo);

			final TrNode node = new TrNode(joinerDir, joinerConfig);
			joiners.add(node);
		}

		Thread.sleep(20000);

		final StringBuilder builder = new StringBuilder();
		builder.append(getVertex(seedNode));
		for (final TrNode joiner : joiners) {
			builder.append(getVertex(joiner));
		}
		final String graph = builder.toString();
		System.out.println(graph);
		//saveGraph(graph);
	}

	private String getVertex(final TrNode node) throws Exception {
		final StringBuilder builder = new StringBuilder();
		for (final TrPeerInfo o : node.peerManager.peers.values()) {
			final int nodeTopologyLoc = TopologyMaintenanceSessionImpl.calcTopologyLoc(node.getRemoteNodeAddress().publicKey);
			builder.append( nodeTopologyLoc + " -- " + o.topologyLocation + "; ");
		}
		return builder.toString();
	}

	private void saveGraph(final String graph) {
		try {
			final BufferedWriter out = new BufferedWriter(new FileWriter("/home/kieran/tahrir_graph/graph.dot"));
			out.write("graph tahrir_topology_graph { " + graph + " }");
			out.close();
		} catch (final Exception e) {
			System.err.println("Could not save graph");
			System.exit(1);
		}
	}
}
