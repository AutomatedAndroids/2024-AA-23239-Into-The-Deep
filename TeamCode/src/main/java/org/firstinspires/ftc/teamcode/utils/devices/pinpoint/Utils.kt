package org.firstinspires.ftc.teamcode.utils.devices.pinpoint

import com.acmerobotics.roadrunner.Pose2d
import com.acmerobotics.roadrunner.Vector2d
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit

fun Pose2D.rrv(): Vector2d {
    return Vector2d(this.getX(DistanceUnit.INCH), this.getY(DistanceUnit.INCH))
}
fun Pose2D.rrp(): Pose2d {
    return Pose2d(this.rrv(), this.getHeading(AngleUnit.DEGREES));
}