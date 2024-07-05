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

import com.graphhopper.util.Helper;

/**
 * This enum defines the road cyclcability of an edge.
 */
public enum CyclewayLeft {
    NONE, OPPOSITE, SHARED, SHARE_BUSWAY, OPPOSITE_SHARE_BUSWAY, SHARED_LANE, OPPOSITE_SHARED_LANE, LANE, OPPOSITE_LANE, TRACK, OPPOSITE_TRACK, CROSSING, OTHER;

    public static final String KEY = "cycleway_left";

    public static EnumEncodedValue<CyclewayLeft> create() {
        return new EnumEncodedValue<>(KEY, CyclewayLeft.class);
    }

    @Override
    public String toString() { return Helper.toLowerCase(super.toString()); }

    public static CyclewayLeft find(String name) {
        if (Helper.isEmpty(name))
            return NONE;
        try {
            return CyclewayLeft.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
