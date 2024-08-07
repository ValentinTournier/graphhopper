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
package com.graphhopper.routing.ev;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Helper;


public enum RoadType {
    MOTOR, MOTOR_BICYCLE, BUS_BICYCLE, PEDESTRIAN, PEDESTRIAN_BICYCLE, MOTOR_BICYCLE_LANE, MOTOR_BICYCLE_TRACK, PEDESTRIAN_BICYCLE_SEGREGATED,
    CYCLABLE;

    public static final String KEY = "road_type";

    public static EnumEncodedValue<RoadType> create() {
        return new EnumEncodedValue<>(RoadType.KEY, RoadType.class, true);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

}
