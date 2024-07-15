package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;

import java.util.*;
import java.util.stream.Stream;

import static com.graphhopper.routing.ev.RoadType.*;
import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;
import static com.graphhopper.routing.util.parsers.AbstractAverageSpeedParser.getMaxSpeed;
import static com.graphhopper.routing.util.parsers.AbstractAverageSpeedParser.isValidSpeed;

public abstract class BikeCommonPriorityParser implements TagParser {

    // Bicycle tracks subject to compulsory use in Germany and Poland (https://wiki.openstreetmap.org/wiki/DE:Key:cycleway)
    private static final List<String> CYCLEWAY_ACCESS_KEYS = Arrays.asList("cycleway:bicycle", "cycleway:both:bicycle", "cycleway:left:bicycle", "cycleway:right:bicycle");

    // Pushing section highways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    protected final Set<String> preferHighwayTags = new HashSet<>();
    protected final Map<String, PriorityCode> avoidHighwayTags = new HashMap<>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<>();
    protected final Set<String> intendedValues = new HashSet<>(INTENDED);

    // Constants for priority weighting
    protected final Map<RoadType, Double> roadTypePriorityCodes = new HashMap<>();
    protected final Map<String, Double> highwayPriorityCodes = new HashMap<>();
    protected final Map<RoadType, Double> roadTypeCoefficients = new HashMap<>();
    
    protected final DecimalEncodedValue avgSpeedEnc;
    protected final DecimalEncodedValue priorityEnc;
    
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    int avoidSpeedLimit;
    EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    // This is the specific bicycle class
    private String classBicycleKey;

    EnumEncodedValue<RoadType> roadTypeEnc;

    protected BikeCommonPriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue avgSpeedEnc,
                                       EnumEncodedValue<RouteNetwork> bikeRouteEnc, EnumEncodedValue<RoadType> roadTypeEnc) {
        this.bikeRouteEnc = bikeRouteEnc;
        this.priorityEnc = priorityEnc;
        this.avgSpeedEnc = avgSpeedEnc;
        this.roadTypeEnc = roadTypeEnc;

        // priority codes associated to different roadtypes
        roadTypePriorityCodes.put(CYCLABLE, 1.5);
        roadTypePriorityCodes.put(PEDESTRIAN_BICYCLE_SEGREGATED, 1.3);
        roadTypePriorityCodes.put(MOTOR_BICYCLE_TRACK, 1.3);
        roadTypePriorityCodes.put(MOTOR_BICYCLE_LANE, 1.2);
        roadTypePriorityCodes.put(PEDESTRIAN_BICYCLE, 1.15);
        roadTypePriorityCodes.put(PEDESTRIAN, 0.4);
        roadTypePriorityCodes.put(BUS_BICYCLE, 0.9);
        roadTypePriorityCodes.put(MOTOR_BICYCLE, 0.9);
        roadTypePriorityCodes.put(MOTOR, 0.7);

        // values associated to highways to modify priority
        highwayPriorityCodes.put("living_street", 1.);
        highwayPriorityCodes.put("track", 1.);
        highwayPriorityCodes.put("path", 1.);
        highwayPriorityCodes.put("cycleway", 1.);

        highwayPriorityCodes.put("residential", 1.);
        highwayPriorityCodes.put("service", 1.);
        highwayPriorityCodes.put("platform", 1.);
        highwayPriorityCodes.put("unclassified", 1.);
        highwayPriorityCodes.put("tertiary", 0.95);
        highwayPriorityCodes.put("secondary", 0.9);
        highwayPriorityCodes.put("primary", 0.85);

        // coefficients that represent the proximity to other vehicle and so how much highway priority codes will impact final priority
        roadTypeCoefficients.put(MOTOR_BICYCLE_TRACK, 1.);
        roadTypeCoefficients.put(MOTOR_BICYCLE_LANE, 1.);
        roadTypeCoefficients.put(BUS_BICYCLE, 0.9);
        roadTypeCoefficients.put(MOTOR_BICYCLE, 0.9);
        roadTypeCoefficients.put(MOTOR, 0.8);




        // duplicate code as also in BikeCommonAverageSpeedParser
//        addPushingSection("footway");
//        addPushingSection("pedestrian");
//        addPushingSection("steps");
//        addPushingSection("platform");

//        unpavedSurfaceTags.add("unpaved");
//        unpavedSurfaceTags.add("gravel");
//        unpavedSurfaceTags.add("ground");
//        unpavedSurfaceTags.add("dirt");
//        unpavedSurfaceTags.add("grass");
//        unpavedSurfaceTags.add("compacted");
//        unpavedSurfaceTags.add("earth");
//        unpavedSurfaceTags.add("fine_gravel");
//        unpavedSurfaceTags.add("grass_paver");
//        unpavedSurfaceTags.add("ice");
//        unpavedSurfaceTags.add("mud");
//        unpavedSurfaceTags.add("salt");
//        unpavedSurfaceTags.add("sand");
//        unpavedSurfaceTags.add("wood");

//        avoidHighwayTags.put("motorway", REACH_DESTINATION);
//        avoidHighwayTags.put("motorway_link", REACH_DESTINATION);
//        avoidHighwayTags.put("trunk", REACH_DESTINATION);
//        avoidHighwayTags.put("trunk_link", REACH_DESTINATION);
//        avoidHighwayTags.put("primary", BAD);
//        avoidHighwayTags.put("primary_link", BAD);
//        avoidHighwayTags.put("secondary", AVOID);
//        avoidHighwayTags.put("secondary_link", AVOID);
//        avoidHighwayTags.put("bridleway", AVOID);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, PREFER.getValue());

        avoidSpeedLimit = 71;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        Integer priorityFromRelation = routeMap.get(bikeRouteEnc.getEnum(false, edgeId, edgeIntAccess));
        if (highwayValue == null) {
            if (FerrySpeedCalculator.isFerry(way)) {
                priorityFromRelation = SLIGHT_AVOID.getValue();
            } else {
                return;
            }
        }

        //double maxSpeed = Math.max(avgSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), avgSpeedEnc.getDecimal(true, edgeId, edgeIntAccess));
        // default
        priorityEnc.setDecimal(false, edgeId, edgeIntAccess, 1.);
        priorityEnc.setDecimal(true, edgeId, edgeIntAccess, 1.);

        // set priority
        RoadType roadTypeFwd = roadTypeEnc.getEnum(false, edgeId, edgeIntAccess);
        RoadType roadTypeBwd = roadTypeEnc.getEnum(true, edgeId, edgeIntAccess);
        setPriority(false, edgeId, edgeIntAccess, roadTypeFwd, way);
        setPriority(true, edgeId, edgeIntAccess, roadTypeBwd, way);

        //classify(edgeId,edgeIntAccess,way);
        //priorityEnc.setDecimal(false, edgeId, edgeIntAccess, PriorityCode.getValue(handlePriority(way, maxSpeed, priorityFromRelation)));
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and avoid big roads
     * or roads with trams or pedestrian.
     *
     * @return new priority based on priorityFromRelation and on the tags in ReaderWay.
     */
    int handlePriority(ReaderWay way, double wayTypeSpeed, Integer priorityFromRelation) {
        TreeMap<Double, PriorityCode> weightToPrioMap = new TreeMap<>();
        if (priorityFromRelation == null)
            weightToPrioMap.put(0d, UNCHANGED);
        else
            weightToPrioMap.put(110d, PriorityCode.valueOf(priorityFromRelation));

        collect(way, wayTypeSpeed, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue().getValue();
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private PriorityCode convertClassValueToPriority(String tagvalue) {
        int classvalue;
        try {
            classvalue = Integer.parseInt(tagvalue);
        } catch (NumberFormatException e) {
            return UNCHANGED;
        }

        switch (classvalue) {
            case 3:
                return BEST;
            case 2:
                return VERY_NICE;
            case 1:
                return PREFER;
            case -1:
                return SLIGHT_AVOID;
            case -2:
                return AVOID;
            case -3:
                return AVOID_MORE;
            default:
                return UNCHANGED;
        }
    }

    boolean isOpposite(ReaderWay way) {
        return way.hasTag("oneway", "yes") && !way.hasTag("oneway:bicycle", "no");
    }
    
    void setPriority(boolean reverse,  int edgeId, EdgeIntAccess edgeIntAccess, RoadType roadType, ReaderWay way) {
        double pc = roadTypePriorityCodes.getOrDefault(roadType, 1.);
        if (List.of(MOTOR_BICYCLE_TRACK, MOTOR_BICYCLE_LANE, BUS_BICYCLE, MOTOR_BICYCLE, MOTOR).contains(roadType)){
            pc = pc * highwayPriorityCodes.getOrDefault(way.getTag("highway"), 1.) * roadTypeCoefficients.getOrDefault(roadType, 1.);
        }

        priorityEnc.setDecimal(reverse, edgeId, edgeIntAccess, pc);
    }
//
//    void isSegregated(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
//        if (way.hasTag("segregated", "yes")) {
//            String roadClass = "pedestrian_bicycle_segregated";
//            /* ---------- oneway:bicycle? ---------- */
//            if (way.hasTag("oneway:bicycle", "yes")) {
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                // pedestrian_bicycle_segregated -fwd;
//            } else if (way.hasTag("oneway:bicycle", "no")){
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // pedestrian_bicycle_segregated-fwd-bwd;
//            } else if (way.hasTag("oneway:bicycle", "-1")){
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // pedestrian_bicycle_segregated-bwd;
//            } else { // oneway:bicycle = yes or none
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                // pedestrian_bicycle_segregated-fwd;
//            }
//        } else { // segregated = no or none
//            String roadClass = "pedestrian_bicycle";
//            setPriority(false, edgeId, edgeIntAccess, roadClass);
//            setPriority(true, edgeId, edgeIntAccess, roadClass);
//            // pedestrian_bicycle-fwd-bwd;
//        }
//    }
//
//    void noInfra(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
//        String roadClass = "motor";
//        if (way.hasTag("oneway", "yes")) {
//            setPriority(false, edgeId, edgeIntAccess, roadClass);
//            // motor-fwd;
//        } if (way.hasTag("oneway", "-1")) {
//            setPriority(true, edgeId, edgeIntAccess, roadClass);
//            // motor-bwd;
//        } else {
//            setPriority(false, edgeId, edgeIntAccess, roadClass);
//            setPriority(true, edgeId, edgeIntAccess, roadClass);
//            // motor-fwd-bwd;
//        }
//    }
//
//    void cyclewayHandler(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
//        /* ---------- cycleway? ---------- */
//        if (way.hasTag("cycleway", "opposite")) {
//            String roadClass = "motor_bicycle";
//            setPriority(true, edgeId, edgeIntAccess, roadClass);
//            // motor_bicycle-bwd;
//        } else if (way.hasTag("cycleway", "opposite_share_busway", "opposite_shared_busway")) {
//            String roadClass = "bus_bicycle";
//            setPriority(false, edgeId, edgeIntAccess, roadClass);
//            // bus_bicycle-bwd;
//        } else if (way.hasTag("cycleway", "share_busway", "shared_busway")) {
//            String roadClass = "bus_bicycle";
//
//            /* ----------- isOpposite? ----------*/
//            if (isOpposite(way)) {
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // bus_bicycle-bwd;
//            } else {
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                // bus_bicycle-fwd;
//            }
//        } else if (way.hasTag("cycleway", "share", "shared")) {
//            String roadClass = "motor_bicycle";
//
//            /* ----------- isOpposite? ----------*/
//            if (isOpposite(way)) {
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle-bwd;
//            } else {
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle-fwd;
//            }
//        } else if (way.hasTag("cycleway", "track")) {
//            String roadClass = "motor_bicycle_track";
//
//            /* ---------- oneway:bicycle? ---------- */
//            if (way.hasTag("oneway:bicycle", "yes")) {
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_track-fwd;
//            } else if (way.hasTag("oneway:bicycle", "-1")) {
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_track-bwd;
//            } else if (way.hasTag("oneway:bicycle", "no")) {
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_track-fwd-bwd;
//            } else { // oneway:bicycle = none
//                if (way.hasTag("oneway", "-1")) {
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-bwd;
//                } else { // oneway = no or none
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-fwd-bwd;
//                }
//            }
//        } else if (way.hasTag("cycleway", "opposite_track")) {
//            String roadClass = "motor_bicycle_track";
//            setPriority(true, edgeId, edgeIntAccess, roadClass);
//            // motor_bicycle_track-bwd;
//        } else if (way.hasTag("cycleway", "lane")) {
//            String roadClass = "motor_bicycle_lane";
//
//            /* ---------- oneway:bicycle? ---------- */
//            if (way.hasTag("oneway:bicycle", "yes")) {
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_lane-fwd;
//            } else if (way.hasTag("oneway:bicycle", "-1")) {
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_lane-bwd;
//            } else if (way.hasTag("oneway:bicycle", "no")) {
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_lane-fwd-bwd;
//            } else { // oneway:bicycle = none
//                if (way.hasTag("oneway", "-1")) {
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-bwd;
//                } else { // oneway = no or none
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-fwd-bwd;
//                }
//            }
//        } else if (way.hasTag("cycleway", "opposite_lane")) {
//            String roadClass = "motor_bicycle_lane";
//            setPriority(true, edgeId, edgeIntAccess, roadClass);
//            // motor_bicycle_lane-bwd;
//        } else if (way.hasTag("cycleway", "no")){
//            noInfra(edgeId, edgeIntAccess, way);
//        }
//    }
//
//    void classify(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
//        /* ---------- highway? ---------- */
//        if (way.hasTag("highway", "cycleway")) {
//
//            /* ---------- foot? ---------- */
//            if (way.hasTag("foot", intendedValues)){
//
//                /* ---------- segregated? ---------- */
//                isSegregated(edgeId, edgeIntAccess, way);
//
//            } else { // foot = restricted
//                String roadClass = "cyclable";
//
//                /* ---------- oneway? ---------- */
//                if (way.hasTag("oneway", "no")){
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    // cyclable-fwd-bwd
//                } else if (way.hasTag("oneway", "-1")) {
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // cyclable-bwd
//                } else { // oneway = yes or none
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    // cyclable-fwd
//                }
//            }
//        } else if (way.hasTag("highway", "path", "footway")) {
//
//            /* ---------- bicycle? ---------- */
//            if (way.hasTag("bicycle", intendedValues)){
//                isSegregated(edgeId, edgeIntAccess, way);
//            } else { // bicycle = restricted
//                String roadClass = "pedestrian";
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // pedestrian-fwd-bwd
//            }
//        } else { // primary, secondary, tertiary, residential, service ...
//            if (way.hasTag("cycleway")) {
//                 cyclewayHandler(edgeId, edgeIntAccess, way);
//            /* ---------- cycleway:both? ---------- */
//            } else if (way.hasTag("cycleway:both")) {
//                if (way.hasTag("cycleway:both", "track")){
//                    String roadClass = "motor_bicycle_track";
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    //motor_bicycle_track-fwd-bwd
//                } else if (way.hasTag("cycleway:both", "lane")){
//                    String roadClass = "motor_bicycle_lane";
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-fwd-bwd
//                } else if (way.hasTag("cycleway:both", "no")){} {
//                    noInfra(edgeId, edgeIntAccess, way);
//                }
//
//            /* ---------- cycleway:left? ---------- */
//            } else if (way.hasTag("cycleway:left", "opposite_track")) {
//                String roadClass = "motor_bicycle_track";
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//            } else if (way.hasTag("cycleway:left", "track")) {
//                String roadClass = "motor_bicycle_track";
//                /* ---------- cycleway:left:oneway? ---------- */
//                if (!way.hasTag("cycleway:left:oneway") || way.hasTag("cycleway:left:oneway", "yes")){
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-bwd;
//                } else if (way.hasTag("cycleway:left:oneway", "-1")) {
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-fwd;
//                } else if (way.hasTag("cycleway:left:oneway", "no")) {
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-fwd-bwd
//                }
//            } else if (way.hasTag("cycleway:left", "opposite_lane")) {
//                String roadClass = "motor_bicycle_lane";
//                setPriority(false, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_lane-fwd;
//            } else if (way.hasTag("cycleway:left", "lane")) {
//                String roadClass = "motor_bicycle_lane";
//                /* ---------- cycleway:left:oneway? ---------- */
//                if (!way.hasTag("cycleway:left:oneway") || way.hasTag("cycleway:left:oneway", "yes")){
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-bwd;
//                } else if (way.hasTag("cycleway:left:oneway", "-1")) {
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-fwd;
//                } else if (way.hasTag("cycleway:left:oneway", "no")) {
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-fwd-bwd;
//                }
//            } else if (way.hasTag("cycleway:left", "no")) {
//                noInfra(edgeId, edgeIntAccess, way); // peut poser problème
//
//            /* ---------- cycleway:right? ---------- */
//            } else if (way.hasTag("cycleway:right", "opposite_track")) {
//                String roadClass = "motor_bicycle_track";
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_track-bwd;
//            } else if (way.hasTag("cycleway:right", "track")) {
//                String roadClass = "motor_bicycle_track";
//                /* ---------- cycleway:right:oneway? ---------- */
//                if (!way.hasTag("cycleway:right:oneway") || way.hasTag("cycleway:right:oneway", "yes")){
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-fwd;
//                } else if (way.hasTag("cycleway:right:oneway", "-1")) {
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-bwd;
//                } else if (way.hasTag("cycleway:right:oneway", "no")) {
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_track-fwd-bwd;
//                }
//            } else if (way.hasTag("cycleway:right", "opposite_lane")) {
//                String roadClass = "motor_bicycle_lane";
//                setPriority(true, edgeId, edgeIntAccess, roadClass);
//                // motor_bicycle_lane-bwd;
//            } else if (way.hasTag("cycleway:right", "lane")) {
//                String roadClass = "motor_bicycle_lane";
//                /* ---------- cycleway:right:oneway? ---------- */
//                if (!way.hasTag("cycleway:right:oneway") || way.hasTag("cycleway:right:oneway", "yes")){
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-fwd;
//                } else if (way.hasTag("cycleway:right:oneway", "-1")) {
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-bwd;
//                } else if (way.hasTag("cycleway:right:oneway", "no")) {
//                    setPriority(false, edgeId, edgeIntAccess, roadClass);
//                    setPriority(true, edgeId, edgeIntAccess, roadClass);
//                    // motor_bicycle_lane-fwd-bwd;
//                }
//            } else if (way.hasTag("cycleway:right", "no")) {
//                noInfra(edgeId, edgeIntAccess, way); // peut poser problème
//            } else {
//                noInfra(edgeId, edgeIntAccess, way);
//            }
//        }
//    }
    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, PriorityCode> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (isDesignated(way)) {
            if ("path".equals(highway))
                weightToPrioMap.put(100d, VERY_NICE);
            else
                weightToPrioMap.put(100d, PREFER);
        }

        if ("cycleway".equals(highway)) {
            if (way.hasTag("foot", intendedValues) && !way.hasTag("segregated", "yes"))
                weightToPrioMap.put(100d, PREFER);
            else
                weightToPrioMap.put(100d, VERY_NICE);
        }

        double maxSpeed = Math.max(getMaxSpeed(way, false), getMaxSpeed(way, true));
        if (preferHighwayTags.contains(highway) || (isValidSpeed(maxSpeed) && maxSpeed <= 30)) {
            if (!isValidSpeed(maxSpeed) || maxSpeed < avoidSpeedLimit) {
                weightToPrioMap.put(40d, PREFER);
                if (way.hasTag("tunnel", intendedValues))
                    weightToPrioMap.put(40d, UNCHANGED);
            }
        } else if (avoidHighwayTags.containsKey(highway)
                || isValidSpeed(maxSpeed) && maxSpeed >= avoidSpeedLimit && !"track".equals(highway)) {
            PriorityCode priorityCode = avoidHighwayTags.get(highway);
            weightToPrioMap.put(50d, priorityCode == null ? AVOID : priorityCode);
            if (way.hasTag("tunnel", intendedValues)) {
                PriorityCode worse = priorityCode == null ? BAD : priorityCode.worse().worse();
                weightToPrioMap.put(50d, worse == EXCLUDE ? REACH_DESTINATION : worse);
            }
        }

        List<String> cyclewayValues = Stream.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right").map(key -> way.getTag(key, "")).toList();
        if (cyclewayValues.contains("track")) {
            weightToPrioMap.put(100d, PREFER);
        } else if (Stream.of("lane", "opposite_track", "shared_lane", "share_busway", "shoulder").anyMatch(cyclewayValues::contains)) {
            weightToPrioMap.put(100d, SLIGHT_PREFER);
        }

        if (way.hasTag("bicycle", "use_sidepath")) {
            weightToPrioMap.put(100d, REACH_DESTINATION);
        }

        if (pushingSectionsHighways.contains(highway) || "parking_aisle".equals(way.getTag("service"))) {
            PriorityCode pushingSectionPrio = SLIGHT_AVOID;
            if (way.hasTag("bicycle", "yes") || way.hasTag("bicycle", "permissive"))
                pushingSectionPrio = PREFER;
            if (isDesignated(way) && (!way.hasTag("highway", "steps")))
                pushingSectionPrio = VERY_NICE;
            if (way.hasTag("foot", "yes")) {
                pushingSectionPrio = pushingSectionPrio.worse();
                if (way.hasTag("segregated", "yes"))
                    pushingSectionPrio = pushingSectionPrio.better();
            }
            if (way.hasTag("highway", "steps")) {
                pushingSectionPrio = BAD;
            }
            weightToPrioMap.put(100d, pushingSectionPrio);
        }

        if (way.hasTag("railway", "tram"))
            weightToPrioMap.put(50d, AVOID_MORE);

        if (way.hasTag("lcn", "yes"))
            weightToPrioMap.put(100d, PREFER);

        String classBicycleValue = way.getTag(classBicycleKey);
        if (classBicycleValue != null) {
            // We assume that humans are better in classifying preferences compared to our algorithm above -> weight = 100
            weightToPrioMap.put(100d, convertClassValueToPriority(classBicycleValue));
        } else {
            String classBicycle = way.getTag("class:bicycle");
            if (classBicycle != null)
                weightToPrioMap.put(100d, convertClassValueToPriority(classBicycle));
        }

        // Increase the priority for scenic routes or in case that maxspeed limits our average speed as compensation. See #630
        if (way.hasTag("scenic", "yes") || maxSpeed > 0 && maxSpeed <= wayTypeSpeed) {
            PriorityCode lastEntryValue = weightToPrioMap.lastEntry().getValue();
            if (lastEntryValue.getValue() < BEST.getValue())
                weightToPrioMap.put(110d, lastEntryValue.better());
        }
    }

    boolean isDesignated(ReaderWay way) {
        return way.hasTag("bicycle", "designated") || way.hasTag(CYCLEWAY_ACCESS_KEYS, "designated")
                || way.hasTag("bicycle_road", "yes") || way.hasTag("cyclestreet", "yes") || way.hasTag("bicycle", "official");
    }

    // TODO duplicated in average speed
    void addPushingSection(String highway) {
        pushingSectionsHighways.add(highway);
    }

    void setSpecificClassBicycle(String subkey) {
        classBicycleKey = "class:bicycle:" + subkey;
    }

    public final DecimalEncodedValue getPriorityEnc() {
        return priorityEnc;
    }
}
