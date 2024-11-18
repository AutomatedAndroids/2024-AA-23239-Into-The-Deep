package com.acmerobotics.roadrunner.ftc.pinpoint

import com.acmerobotics.roadrunner.Pose2d
import com.acmerobotics.roadrunner.Vector2d
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit

class Pose2D (
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

fun Pose2D.rrv(): Vector2d {
    return Vector2d(this.getX(DistanceUnit.INCH), this.getY(DistanceUnit.INCH))
}
fun Pose2D.rrp(): Pose2d {
    return Pose2d(this.rrv(), this.getHeading(AngleUnit.DEGREES));
}


