package playground.andreas.intersection.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

import org.apache.log4j.Logger;
import org.matsim.events.EventLinkEnter;
import org.matsim.gbl.Gbl;
import org.matsim.mobsim.QueueLink;
import org.matsim.mobsim.QueueNetworkLayer;
import org.matsim.mobsim.QueueNode;
import org.matsim.mobsim.SimulationTimer;
import org.matsim.mobsim.Vehicle;
import org.matsim.network.Link;
import org.matsim.trafficlights.data.SignalLane;
import org.matsim.trafficlights.data.TrafficLightsManager;
import org.matsim.utils.misc.Time;
import org.matsim.utils.vis.snapshots.writers.PositionInfo;

import playground.andreas.intersection.tl.CalculateAngle;

public class QLink extends QueueLink {

	final private static Logger log = Logger.getLogger(QLink.class);
	private static int spaceCapWarningCount = 0;

	private PseudoLink originalLink = null;

	private ArrayList<PseudoLink> pseudoLinksList = new ArrayList<PseudoLink>();
	private ArrayList<PseudoLink> nodePseudoLinksList;
	

	/** FreeLinkTravelTime */
	private double freeSpeedTT;
	private double effectiveCelleSize;
	
	public QLink(final Link l, final QueueNetworkLayer queueNetworkLayer, final QueueNode toNode) {
		super(l, queueNetworkLayer, toNode);
		
		this.effectiveCelleSize = queueNetworkLayer.getNetworkLayer().getEffectiveCellSize();

		this.freeSpeedTT = this.getLink().getLength() / this.getLink().getFreespeed(Time.UNDEFINED_TIME);
		// Original LinkErstellen
		this.originalLink = new PseudoLink(this, true);
		// Konfigurieren
		if(! this.originalLink.recalculatePseudoLinkProperties(0., this.getLink().getLength(), this.getLink().getLanesAsInt(org.matsim.utils.misc.Time.UNDEFINED_TIME), this.getLink().getFreespeed(Time.UNDEFINED_TIME), this.getLink().getFlowCapacity(org.matsim.utils.misc.Time.UNDEFINED_TIME),this.effectiveCelleSize)) {

			if ( spaceCapWarningCount <=10 ) {
				log.warn("Link " + this.getLink().getId() + " too small: enlarge spaceCap.  This is not fatal, but modifies the traffic flow dynamics.");
				if ( spaceCapWarningCount == 10 ) {
					log.warn("Additional warnings of this type are suppressed.");
				}
				spaceCapWarningCount++ ;
			}

		}

		this.pseudoLinksList.add(this.originalLink);
	}

	/** Adds a vehicle to the parkingQueue */
	public void addVehicle2ParkingQueue(QVehicle veh) {
		this.originalLink.addVehicle2ParkingQueue(veh);
	}	

	@Override
	public void addParking(Vehicle veh) {
		this.originalLink.addVehicle2ParkingQueue((QVehicle)veh);
	}

	public double getFreeSpeedTT() {
		return this.freeSpeedTT;
	}

	/** Called by QNetworkLayer */
	@Override
	public boolean moveLink(final double now) {

		for (PseudoLink pseudoLink : this.pseudoLinksList) {
			pseudoLink.movePseudoLink(now);
		}
		return true ;
	}

	@Override
	public boolean hasSpace() {
		return this.originalLink.hasSpace();
	}

	@Override
	public void add(final Vehicle veh) {
		
		double now = SimulationTimer.getTime();
		activateLink();
		veh.setCurrentLink(this.getLink());
		this.originalLink.addVehicle((QVehicle)veh);
				
		veh.setDepartureTime_s((int) (now + this.originalLink.getFreeSpeedTravelTime()));
		QSim.getEvents().processEvent(
				new EventLinkEnter(now, veh.getDriver().getId().toString(),	veh.getCurrentLegNumber(),
						this.getLink().getId().toString(), veh.getDriver(), this.getLink()));
	}

	public List<PseudoLink> getNodePseudoLinks(){		
		if (this.nodePseudoLinksList == null && this.pseudoLinksList.size() == 1){
			return this.pseudoLinksList;
		} else {
			return this.nodePseudoLinksList;
		}
	}
	
	public List<PseudoLink> getNodePseudoLinks(List<Link> destLinks){		
		
		if (this.nodePseudoLinksList == null && this.pseudoLinksList.size() == 1){
			return this.pseudoLinksList;
		}
		
		List<PseudoLink> returnPseudoLinkList = new ArrayList<PseudoLink>();
		for (PseudoLink pseudoLink : this.nodePseudoLinksList) {
			for (Link destLink : destLinks) {
				if(pseudoLink.getDestLinks().contains(destLink)){
					returnPseudoLinkList.add(pseudoLink);
				}
			}			
		}
		return returnPseudoLinkList;
	}

	public void reconfigure(TrafficLightsManager trafficLightsManager) {

		boolean firstNodeLinkInitialized = false;

		for (SignalLane signalLane : trafficLightsManager.getFromLanes(this.getLink().getId())) {

			int numberOfLanes_ = 1;
			double freeSpeed_m_s = this.getLink().getFreespeed(Time.UNDEFINED_TIME);
			double flowCapacity_Veh_h = this.getSimulatedFlowCapacity();

			//TODO [an] has to be checked, not sure about it

			PseudoLink newNodePseudoLink;

			if(!firstNodeLinkInitialized){

				newNodePseudoLink = new PseudoLink(this, false);

				newNodePseudoLink.setFromLink(this.originalLink);
				this.originalLink.getToLinks().add(newNodePseudoLink);

				for (Link link : trafficLightsManager.getToLinks(signalLane)) {
					newNodePseudoLink.addDestLink(link);
					this.originalLink.addDestLink(link);
				}

				newNodePseudoLink.recalculatePseudoLinkProperties(0, signalLane.getLength(), numberOfLanes_, freeSpeed_m_s, flowCapacity_Veh_h, this.effectiveCelleSize);
				this.originalLink.recalculatePseudoLinkProperties(signalLane.getLength(), this.getLink().getLength() - signalLane.getLength(), this.getLink().getLanesAsInt(org.matsim.utils.misc.Time.UNDEFINED_TIME), this.getLink().getFreespeed(Time.UNDEFINED_TIME), this.getSimulatedFlowCapacity(),this.effectiveCelleSize);

				this.pseudoLinksList.add(newNodePseudoLink);
				firstNodeLinkInitialized = true;

			} else {

				// Now we have the original link and one extension pseudo Link
				// therefore add additional extension links for the rest of the outLinks

				// Check, if the new extension link is in proximity of an old one's staring point
				if (signalLane.getLength() - this.originalLink.getMeterFromLinkEnd() > 15.0){
					// It is not

					System.err.println("Not Implemented yet: Every PseudoNode in 15m proximity of an old PseudoNode will be ajected to the old one");

					// Insert new one...
					// Fix Pointer...
					// Adjust SC, SQ...
					// Generate new NodePseudoLink
					// Fix Pointer...
					// Adjust SC, SQ...

				}else{
					// It is
					// New NodePseudoLink will start at originalLink

					newNodePseudoLink = new PseudoLink(this, false);

					newNodePseudoLink.setFromLink(this.originalLink);
					this.originalLink.getToLinks().add(newNodePseudoLink);

					for (Link link : trafficLightsManager.getToLinks(signalLane)) {
						newNodePseudoLink.addDestLink(link);
						this.originalLink.addDestLink(link);
					}

					// Only need to fix properties of new link. Original link hasn't changed
					newNodePseudoLink.recalculatePseudoLinkProperties(0, signalLane.getLength(), numberOfLanes_, freeSpeed_m_s, flowCapacity_Veh_h,this.effectiveCelleSize);

					this.pseudoLinksList.add(newNodePseudoLink);

				}

			}

		}

		findLayout();

		addUTurn();

		resortPseudoLinks();
	}
	
	private void resortPseudoLinks(){
		this.nodePseudoLinksList = new ArrayList<PseudoLink>();

		for (PseudoLink pseudoLink : this.pseudoLinksList) {
			if (pseudoLink.getMeterFromLinkEnd() == 0){
				this.nodePseudoLinksList.add(pseudoLink);								
			}
		}
		
		Collections.sort(this.pseudoLinksList);
		
//		for (PseudoLink pseudoLink : nodePseudoLinksList){
//			this.pseudoLinksList.remove(pseudoLink);
//		}
	}
	
	private void addUTurn(){
		
		for (Link outLink : this.getLink().getToNode().getOutLinks().values()) {
			
			if((outLink.getToNode().equals(this.getLink().getFromNode()))){
				
				PseudoLink tempPseudoLink = null;
				for (PseudoLink pseudoLink : this.pseudoLinksList) {
					
					if( tempPseudoLink == null || (pseudoLink.lane == 1 && pseudoLink.getMeterFromLinkEnd() == 0)){
						tempPseudoLink = pseudoLink;
					}	
										
				}
				
				tempPseudoLink.addDestLink(outLink);
				this.originalLink.addDestLink(outLink);	
				
			}
		}
		
		
	}
	
	private void findLayout(){
		
		SortedMap<Double, Link> result = CalculateAngle.getOutLinksSortedByAngle(this.getLink());
		
		for (PseudoLink pseudoLink : this.pseudoLinksList) {
			int lane = 1;
			for (Link link : result.values()) {
				if (pseudoLink.getDestLinks().contains(link)){
					pseudoLink.lane = lane;
					break;
				} else {
					lane++;
				}
			}			
		}		
	}
	
	@Override
	public Collection<PositionInfo> getVehiclePositions(final Collection<PositionInfo> positions) {
		String snapshotStyle = Gbl.getConfig().simulation().getSnapshotStyle();
		if ("queue".equals(snapshotStyle)) {
			getVehiclePositionsQueue(positions);			
		} else {
			log.warn("The snapshotStyle \"" + snapshotStyle + "\" is not supported.");
		}
		return positions;
	}
	
	/**
	 * Calculates the positions of all vehicles on this link according to the
	 * queue-logic: Vehicles are placed on the link according to the ratio between
	 * the free-travel time and the time the vehicles are already on the link. If
	 * they could have left the link already (based on the time), the vehicles
	 * start to build a traffic-jam (queue) at the end of the link.
	 *
	 * @param positions
	 *          A collection where the calculated positions can be stored.
	 */
	@Override
	public void getVehiclePositionsQueue(final Collection<PositionInfo> positions) {
		
		for (PseudoLink pseudoLink : this.pseudoLinksList) {
			pseudoLink.getVehPositions(positions);
		}		
		
	}
}
