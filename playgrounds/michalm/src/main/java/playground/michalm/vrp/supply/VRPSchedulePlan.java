package playground.michalm.vrp.supply;

import java.util.*;

import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.*;
import org.matsim.core.population.routes.*;
import org.matsim.core.utils.misc.*;

import pl.poznan.put.vrp.dynamic.data.model.*;
import pl.poznan.put.vrp.dynamic.data.schedule.*;
import playground.michalm.vrp.data.*;
import playground.michalm.vrp.data.network.*;
import playground.michalm.vrp.data.network.ShortestPath.SPEntry;


public class VRPSchedulePlan
    extends PlanImpl
{
    private PopulationFactory populFactory;
    private Network network;
    private ShortestPath[][] shortestPaths;

    private Vehicle vehicle;


    public VRPSchedulePlan(Person driver, Vehicle vehicle, MATSimVRPData data)
    {
        super(driver);
        this.vehicle = vehicle;

        populFactory = data.getScenario().getPopulation().getFactory();
        network = data.getScenario().getNetwork();
        shortestPaths = data.getVrpGraph().getShortestPaths();

        init();
    }


    private void init()
    {
        MATSimVertex depotVertex = (MATSimVertex)vehicle.getDepot().getVertex();

        Schedule schedule = vehicle.getSchedule();
        
        if (schedule.getStatus().isUnplanned()) {// vehicle stays at the depot
            addActivity(depotVertex, -1, "RtU");
            return;
        }

        // Depot - before schedule.getBeginTime()
        addActivity(depotVertex, schedule.getBeginTime(), "RtP");

        for (Task t : schedule.getTasks()) {
            switch (t.getType()) {
                case DRIVE:
                    DriveTask dt = (DriveTask)t;
                    addLeg((MATSimVertex)dt.getFromVertex(), (MATSimVertex)dt.getToVertex(),
                            dt.getBeginTime(), dt.getEndTime());
                    break;

                case SERVE:
                    ServeTask st = (ServeTask)t;
                    addActivity((MATSimVertex)st.getAtVertex(), st.getEndTime(), ""
                            + st.getRequest().getId());
                    break;

                case WAIT:
                    WaitTask wt = (WaitTask)t;
                    addActivity((MATSimVertex)wt.getAtVertex(), wt.getEndTime(), "W");
                    break;

                default:
                    throw new IllegalStateException();
            }
        }

        // Depot - after schedule.getEndTime()
        addActivity(depotVertex, -1, "RtC");
    }


    private void addLeg(MATSimVertex fromVertex, MATSimVertex toVertex, int departTime,
            int arrivalTime)
    {
        ShortestPath sp = shortestPaths[fromVertex.getId()][toVertex.getId()];

        Leg leg = populFactory.createLeg(TransportMode.car);

        leg.setDepartureTime(departTime);

        Link fromLink = fromVertex.getLink();
        Link toLink = toVertex.getLink();

        SPEntry entry = sp.getSPEntry(departTime);
        Id[] linkIds = entry.linkIds;

        NetworkRoute netRoute = (NetworkRoute) ((PopulationFactoryImpl)populFactory).createRoute(
                TransportMode.car, fromLink.getId(), toLink.getId());

        if (linkIds.length > 0) {// means: fromLink != toLink

            // all except the last one (it's the toLink)
            ArrayList<Id> linkIdList = new ArrayList<Id>(linkIds.length - 1);

            for (int i = 0; i < linkIds.length - 1; i++) {
                linkIdList.add(linkIds[i]);
            }

            netRoute.setLinkIds(fromLink.getId(), Arrays.asList(entry.linkIds), toLink.getId());
            netRoute.setDistance(RouteUtils.calcDistance(netRoute, network));
        }
        else {
            netRoute.setDistance(0.0);
        }

        int travelTime = arrivalTime - departTime;// According to the route

        netRoute.setTravelTime(travelTime);
        netRoute.setTravelCost(entry.travelCost);

        leg.setRoute(netRoute);
        leg.setDepartureTime(departTime);
        leg.setTravelTime(travelTime);
        ((LegImpl)leg).setArrivalTime(arrivalTime);

        addLeg(leg);
    }


    private void addActivity(MATSimVertex vertex, int endTime, String type)
    {
        // Activity act = populFactory.createActivityFromLinkId("service",
        // vertex.getLink().getId());
        Activity act = new ActivityImpl(type, vertex.getCoord(), vertex.getLink().getId());

        if (endTime != -1) {
            act.setEndTime(endTime);
        }

        addActivity(act);
    }
}
