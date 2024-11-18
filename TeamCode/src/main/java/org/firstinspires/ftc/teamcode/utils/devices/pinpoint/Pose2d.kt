package org.firstinspires.ftc.teamcode.utils.devices.pinpoint

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit


class Pose2D(
    private val distanceUnit: DistanceUnit,
    private val x: Double,
    private val y: Double,
    private val headingUnit: AngleUnit,
    private val heading: Double
) {
    fun getX(unit: DistanceUnit): Double {
        return unit.fromUnit(this.distanceUnit, x)
    }

    fun getY(unit: DistanceUnit): Double {
        return unit.fromUnit(this.distanceUnit, y)
    }

    fun getHeading(unit: AngleUnit): Double {
        return unit.fromUnit(this.headingUnit, heading)
    }
}