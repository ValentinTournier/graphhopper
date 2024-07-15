package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.RoadType;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.graphhopper.routing.ev.RoadType.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;


/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * see: associated mermaid graph
 */
public class RoadTypeParser implements TagParser {
    private final EnumEncodedValue<RoadType> roadTypeEnc;
    protected final Set<String> intendedValues = new HashSet<>(INTENDED);

    public RoadTypeParser(EnumEncodedValue<RoadType> roadTypeEnc) {
        this.roadTypeEnc = roadTypeEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        classify(edgeId, edgeIntAccess,way);
    }
    
    boolean isOpposite(ReaderWay way) {
        return way.hasTag("oneway", "yes") && !way.hasTag("oneway:bicycle", "no");
    }

    void isSegregated(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        if (way.hasTag("segregated", "yes")) {
            RoadType roadType = PEDESTRIAN_BICYCLE_SEGREGATED;
            /* ---------- oneway:bicycle? ---------- */
            if (way.hasTag("oneway:bicycle", "yes")) {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                // pedestrian_bicycle_segregated -fwd;
            } else if (way.hasTag("oneway:bicycle", "no")){
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // pedestrian_bicycle_segregated-fwd-bwd;
            } else if (way.hasTag("oneway:bicycle", "-1")){
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // pedestrian_bicycle_segregated-bwd;
            } else { // oneway:bicycle = none
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // pedestrian_bicycle_segregated-fwd-bwd;
            }
        } else { // segregated = no or none
            RoadType roadType = PEDESTRIAN_BICYCLE;
            roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
            roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
            // pedestrian_bicycle-fwd-bwd;
        }
    }

    void noInfra(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        RoadType roadType = MOTOR;
        if (way.hasTag("oneway", "yes")) {
            roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
            // motor-fwd;
        } if (way.hasTag("oneway", "-1")) {
            roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
            // motor-bwd;
        } else {
            roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
            roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
            // motor-fwd-bwd;
        }
    }

    void cyclewayHandler(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        /* ---------- cycleway? ---------- */
        if (way.hasTag("cycleway", "opposite")) {
            roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, MOTOR_BICYCLE);
            // motor_bicycle-bwd;
        } else if (way.hasTag("cycleway", "opposite_share_busway", "opposite_shared_busway")) {
            roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, BUS_BICYCLE);
            // bus_bicycle-bwd;
        } else if (way.hasTag("cycleway", "share_busway", "shared_busway")) {
            RoadType roadType = BUS_BICYCLE;

            /* ----------- isOpposite? ----------*/
            if (isOpposite(way)) {
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // bus_bicycle-bwd;
            } else {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                // bus_bicycle-fwd;
            }
        } else if (way.hasTag("cycleway", "share", "shared", "share_lane", "shared_lane")) {
            RoadType roadType = MOTOR_BICYCLE;

            /* ----------- isOpposite? ----------*/
            if (isOpposite(way)) {
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // motor_bicycle-bwd;
            } else {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                // motor_bicycle-fwd;
            }
        } else if (way.hasTag("cycleway", "track")) {
            RoadType roadType = MOTOR_BICYCLE_TRACK;

            /* ---------- oneway:bicycle? ---------- */
            if (way.hasTag("oneway:bicycle", "yes")) {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                // motor_bicycle_track-fwd;
            } else if (way.hasTag("oneway:bicycle", "-1")) {
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // motor_bicycle_track-bwd;
            } else if (way.hasTag("oneway:bicycle", "no")) {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // motor_bicycle_track-fwd-bwd;
            } else { // oneway:bicycle = none
                if (way.hasTag("oneway", "-1")) {
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-bwd;
                } else { // oneway = no or none
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-fwd-bwd;
                }
            }
        } else if (way.hasTag("cycleway", "lane")) {
            RoadType roadType = MOTOR_BICYCLE_LANE;

            /* ---------- oneway:bicycle? ---------- */
            if (way.hasTag("oneway:bicycle", "yes")) {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                // motor_bicycle_lane-fwd;
            } else if (way.hasTag("oneway:bicycle", "-1")) {
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // motor_bicycle_lane-bwd;
            } else if (way.hasTag("oneway:bicycle", "no")) {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // motor_bicycle_lane-fwd-bwd;
            } else { // oneway:bicycle = none
                if (way.hasTag("oneway", "-1")) {
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-bwd;
                } else { // oneway = no or none
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-fwd-bwd;
                }
            }
        } else if (way.hasTag("cycleway", "opposite_track")) {
            roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, MOTOR_BICYCLE_TRACK);
            // motor_bicycle_track-bwd;
        } else if (way.hasTag("cycleway", "opposite_lane")) {
            roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, MOTOR_BICYCLE_LANE);
            // motor_bicycle_lane-bwd;
        } else if (way.hasTag("cycleway", "no")){
            noInfra(edgeId, edgeIntAccess, way);
        }
    }

    void classify(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        /* ---------- highway? ---------- */
        if (way.hasTag("highway", "cycleway")) {

            /* ---------- foot? ---------- */
            if (way.hasTag("foot", intendedValues)){

                /* ---------- segregated? ---------- */
                isSegregated(edgeId, edgeIntAccess, way);

            } else { // foot = restricted
                RoadType roadType = CYCLABLE;

                /* ---------- oneway? ---------- */
                if (way.hasTag("oneway", "no") || way.hasTag("cycleway:oneway", "no") || way.hasTag("oneway:bicycle", "no")){
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    // cyclable-fwd-bwd
                } else if (way.hasTag("oneway", "-1") || way.hasTag("cycleway:oneway", "-1") || way.hasTag("oneway:bicycle", "-1")) {
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // cyclable-bwd
                } else if (way.hasTag("oneway", "yes")) { // oneway = yes
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    // cyclable-fwd
                } else {
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                }
            }
        } else if (way.hasTag("highway", "path", "footway")) {

            /* ---------- bicycle? ---------- */
            if (way.hasTag("bicycle", intendedValues)){
                isSegregated(edgeId, edgeIntAccess, way);
            } else { // bicycle = restricted
                RoadType roadType = PEDESTRIAN;
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                // pedestrian-fwd-bwd
            }
        } else { // primary, secondary, tertiary, residential, service ...
            if (way.hasTag("cycleway")) {
                cyclewayHandler(edgeId, edgeIntAccess, way);
                /* ---------- cycleway:both? ---------- */
            } else if (way.hasTag("cycleway:both")) {
                if (way.hasTag("cycleway:both", "track")){
                    RoadType roadType = MOTOR_BICYCLE_TRACK;
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    //motor_bicycle_track-fwd-bwd
                } else if (way.hasTag("cycleway:both", "lane")){
                    RoadType roadType = MOTOR_BICYCLE_LANE;
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-fwd-bwd
                } else if (way.hasTag("cycleway:both", "no")) {
                    noInfra(edgeId, edgeIntAccess, way);
                }

                /* ---------- cycleway:left? ---------- */
            } else if (way.hasTag("cycleway:left", "opposite_track")) {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, MOTOR_BICYCLE_TRACK);
            } else if (way.hasTag("cycleway:left", "track")) {
                RoadType roadType = MOTOR_BICYCLE_TRACK;
                /* ---------- cycleway:left:oneway? ---------- */
                if (way.hasTag("cycleway:left:oneway", "yes")){
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-bwd;
                } else if (way.hasTag("cycleway:left:oneway", "-1")) {
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-bwd;
                } else { //cycleway:left:oneway = no or none
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-fwd-bwd
                }
            } else if (way.hasTag("cycleway:left", "opposite_lane")) {
                roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, MOTOR_BICYCLE_LANE);
                // motor_bicycle_lane-fwd;
            } else if (way.hasTag("cycleway:left", "lane")) {
                RoadType roadType = MOTOR_BICYCLE_LANE;
                /* ---------- cycleway:left:oneway? ---------- */
                if (way.hasTag("cycleway:left:oneway", "yes")) {
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-bwd;
                } else if (way.hasTag("cycleway:left:oneway", "-1")) {
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-fwd;
                } else { //cycleway:left:oneway = no or none
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-fwd-bwd;
                }
            } else if (way.hasTag("cycleway:left", "no")) {
                noInfra(edgeId, edgeIntAccess, way); // peut poser problème

                /* ---------- cycleway:right? ---------- */
            } else if (way.hasTag("cycleway:right", "opposite_track")) {
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, MOTOR_BICYCLE_TRACK);
                // motor_bicycle_track-bwd;
            } else if (way.hasTag("cycleway:right", "track")) {
                RoadType roadType = MOTOR_BICYCLE_TRACK;
                /* ---------- cycleway:right:oneway? ---------- */
                if (way.hasTag("cycleway:right:oneway", "yes")){
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-fwd;
                } else if (way.hasTag("cycleway:right:oneway", "-1")) {
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-bwd;
                } else { //cycleway:right:oneway = no or none
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_track-fwd-bwd;
                }
            } else if (way.hasTag("cycleway:right", "opposite_lane")) {
                roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, MOTOR_BICYCLE_LANE);
                // motor_bicycle_lane-bwd;
            } else if (way.hasTag("cycleway:right", "lane")) {
                RoadType roadType = MOTOR_BICYCLE_LANE;
                /* ---------- cycleway:right:oneway? ---------- */
                if (way.hasTag("cycleway:right:oneway", "yes")){
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-fwd;
                } else if (way.hasTag("cycleway:right:oneway", "-1")) {
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-bwd;
                } else { // cycleway:right:oneway = no or none
                    roadTypeEnc.setEnum(false, edgeId, edgeIntAccess, roadType);
                    roadTypeEnc.setEnum(true, edgeId, edgeIntAccess, roadType);
                    // motor_bicycle_lane-fwd-bwd;
                }
            } else if (way.hasTag("cycleway:right", "no")) {
                noInfra(edgeId, edgeIntAccess, way); // peut poser problème
            } else {
                noInfra(edgeId, edgeIntAccess, way);
            }
        }
    }
}


