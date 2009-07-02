/* *********************************************************************** *
 * project: org.matsim.*
 * CharyparEtAlCompatibleLegTravelTimeEstimatorTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.planomat.costestimators;

import java.util.List;

import org.matsim.core.events.AgentDepartureEvent;
import org.matsim.core.events.BasicEventImpl;
import org.matsim.core.events.Events;
import org.matsim.core.events.LinkEnterEvent;
import org.matsim.core.events.LinkLeaveEvent;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.PlansCalcRoute;
import org.matsim.core.utils.misc.Time;

public class CharyparEtAlCompatibleLegTravelTimeEstimatorTest extends FixedRouteLegTravelTimeEstimatorTest {

	private CharyparEtAlCompatibleLegTravelTimeEstimator testee = null;

	@Override
	protected void tearDown() throws Exception {
		this.testee = null;
		super.tearDown();
	}

	@Override
	public void testGetLegTravelTimeEstimation() {

		PlansCalcRoute plansCalcRoute = new PlansCalcRoute(this.scenario.getConfig().plansCalcRoute(), this.scenario.getNetwork(), this.linkTravelCostEstimator, this.linkTravelTimeEstimator);

		testee = new CharyparEtAlCompatibleLegTravelTimeEstimator(
				this.linkTravelTimeEstimator,
				this.tDepDelayCalc,
				plansCalcRoute);

		Events events = new Events();
		events.addHandler(super.tDepDelayCalc);
		events.addHandler(super.linkTravelTimeEstimator);
		events.printEventHandlers();

		NetworkRoute route = (NetworkRoute) testLeg.getRoute();
		List<LinkImpl> links = route.getLinks();

		// let's test a route without events first
		// should result in free speed travel time, without departure delay
		double departureTime = Time.parseTime("06:03:00");
		double legTravelTime = testee.getLegTravelTimeEstimation(
				TEST_PERSON_ID,
				departureTime,
				originAct,
				destinationAct,
				testLeg);

		double expectedLegEndTime = departureTime;
		expectedLegEndTime += originAct.getLink().getFreespeedTravelTime(Time.UNDEFINED_TIME);
		for (LinkImpl link : links) {
			expectedLegEndTime += link.getFreespeedTravelTime(Time.UNDEFINED_TIME);
		}
		assertEquals(expectedLegEndTime, departureTime + legTravelTime, EPSILON);

		// next, a departure delay of 5s at the origin link is added
		departureTime = Time.parseTime("06:05:00");
		double depDelay = Time.parseTime("00:00:05");
		AgentDepartureEvent depEvent = new AgentDepartureEvent(departureTime, this.testPerson, originAct.getLink(), testLeg);
		LinkLeaveEvent leaveEvent = new LinkLeaveEvent(departureTime + depDelay, this.testPerson, originAct.getLink());

		for (BasicEventImpl event : new BasicEventImpl[]{depEvent, leaveEvent}) {
			events.processEvent(event);
		}

		legTravelTime = testee.getLegTravelTimeEstimation(
				TEST_PERSON_ID,
				departureTime,
				originAct,
				destinationAct,
				testLeg);

		expectedLegEndTime = departureTime;
		expectedLegEndTime += depDelay;
		expectedLegEndTime += originAct.getLink().getFreespeedTravelTime(Time.UNDEFINED_TIME);
		for (LinkImpl link : links) {
			expectedLegEndTime += link.getFreespeedTravelTime(Time.UNDEFINED_TIME);
		}
		assertEquals(expectedLegEndTime, departureTime + legTravelTime, EPSILON);

		// now let's add some travel events
		String[][] eventTimes = new String[][]{
				new String[]{"06:05:00", "06:07:00", "06:09:00"},
				new String[]{"06:16:00", "06:21:00", "06:26:00"}
		};

		BasicEventImpl event = null;
		for (int eventTimesCnt = 0; eventTimesCnt < eventTimes.length; eventTimesCnt++) {
			for (int linkCnt = 0; linkCnt < links.size(); linkCnt++) {
				event = new LinkEnterEvent(
						Time.parseTime(eventTimes[eventTimesCnt][linkCnt]),
						testPerson,
						links.get(linkCnt));
				events.processEvent(event);
				event = new LinkLeaveEvent(
						Time.parseTime(eventTimes[eventTimesCnt][linkCnt + 1]),
						testPerson,
						links.get(linkCnt));
				events.processEvent(event);
			}
		}

		// test a start time where all link departures will be in the first time bin
		departureTime = Time.parseTime("06:10:00");
		legTravelTime = testee.getLegTravelTimeEstimation(
				TEST_PERSON_ID,
				departureTime,
				originAct,
				destinationAct,
				testLeg);
		expectedLegEndTime = departureTime;
		expectedLegEndTime += depDelay;
		expectedLegEndTime = testee.processLink(originAct.getLink(), expectedLegEndTime);
		expectedLegEndTime = testee.processRouteTravelTime(route, expectedLegEndTime);

		assertEquals(expectedLegEndTime, departureTime + legTravelTime, EPSILON);

	}

}
