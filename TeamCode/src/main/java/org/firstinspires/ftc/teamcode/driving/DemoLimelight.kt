/*
Copyright (c) 2024 Limelight Vision

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted (subject to the limitations in the disclaimer below) provided that
the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of FIRST nor the names of its contributors may be used to
endorse or promote products derived from this software without specific prior
written permission.

NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS
LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.firstinspires.ftc.teamcode.driving

import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp

/*
 * This OpMode illustrates how to use the Limelight3A Vision Sensor.
 *
 * @see <a href="https://limelightvision.io/">Limelight</a>
 *
 * Notes on configuration:
 *
 *   The device presents itself, when plugged into a USB port on a Control Hub as an ethernet
 *   interface.  A DHCP server running on the Limelight automatically assigns the Control Hub an
 *   ip address for the new ethernet interface.
 *
 *   Since the Limelight is plugged into a USB port, it will be listed on the top level configuration
 *   activity along with the Control Hub Portal and other USB devices such as webcams.  Typically
 *   serial numbers are displayed below the device's names.  In the case of the Limelight device, the
 *   Control Hub's assigned ip address for that ethernet interface is used as the "serial number".
 *
 *   Tapping the Limelight's name, transitions to a new screen where the user can rename the Limelight
 *   and specify the Limelight's ip address.  Users should take care not to confuse the ip address of
 *   the Limelight itself, which can be configured through the Limelight settings page via a web browser,
 *   and the ip address the Limelight device assigned the Control Hub and which is displayed in small text
 *   below the name of the Limelight on the top level configuration screen.
 */
@TeleOp(name = "Sensor: Limelight3A", group = "Sensor")
class SensorLimelight3A : LinearOpMode() {
    @Throws(InterruptedException::class)
    override fun runOpMode() {
        val limelight = hardwareMap.get(Limelight3A::class.java, "limelight")

        telemetry.msTransmissionInterval = 11

        limelight.pipelineSwitch(0)

        /*
         * Starts polling for data.  If you neglect to call start(), getLatestResult() will return null.
         */
        limelight.start()

        telemetry.addData(">", "Robot Ready.  Press Play.")
        telemetry.update()
        waitForStart()

        while (opModeIsActive()) {
            val status = limelight.getStatus()
            telemetry.addData(
                "Name", "%s",
                status.name
            )
            telemetry.addData(
                "LL", "Temp: %.1fC, CPU: %.1f%%, FPS: %d",
                status.temp, status.cpu, status.fps.toInt()
            )
            telemetry.addData(
                "Pipeline", "Index: %d, Type: %s",
                status.pipelineIndex, status.pipelineType
            )

            val result = limelight.latestResult
            if (result != null) {
                // Access general information
                val botpose = result.botpose
                val captureLatency = result.captureLatency
                val targetingLatency = result.targetingLatency
                val parseLatency = result.parseLatency
                telemetry.addData("LL Latency", captureLatency + targetingLatency)
                telemetry.addData("Parse Latency", parseLatency)
                telemetry.addData("PythonOutput", result.pythonOutput.contentToString())

                if (result.isValid) {
                    telemetry.addData("tx", result.tx)
                    telemetry.addData("txnc", result.txNC)
                    telemetry.addData("ty", result.ty)
                    telemetry.addData("tync", result.tyNC)

                    telemetry.addData("Botpose", botpose.toString())

                    // Access barcode results
                    val barcodeResults = result.barcodeResults
                    for (br in barcodeResults) {
                        telemetry.addData("Barcode", "Data: %s", br.data)
                    }

                    // Access classifier results
                    val classifierResults = result.classifierResults
                    for (cr in classifierResults) {
                        telemetry.addData(
                            "Classifier",
                            "Class: %s, Confidence: %.2f",
                            cr.className,
                            cr.confidence
                        )
                    }

                    // Access detector results
                    val detectorResults = result.detectorResults
                    for (dr in detectorResults) {
                        telemetry.addData(
                            "Detector",
                            "Class: %s, Area: %.2f",
                            dr.className,
                            dr.targetArea
                        )
                    }

                    // Access fiducial results
                    val fiducialResults = result.fiducialResults
                    for (fr in fiducialResults) {
                        telemetry.addData(
                            "Fiducial",
                            "ID: %d, Family: %s, X: %.2f, Y: %.2f",
                            fr.fiducialId,
                            fr.family,
                            fr.targetXDegrees,
                            fr.targetYDegrees
                        )
                    }

                    // Access color results
                    val colorResults = result.colorResults
                    for (cr in colorResults) {
                        telemetry.addData(
                            "Color",
                            "X: %.2f, Y: %.2f",
                            cr.targetXDegrees,
                            cr.targetYDegrees
                        )
                    }
                }
            } else {
                telemetry.addData("Limelight", "No data available")
            }

            telemetry.update()
        }
        limelight.stop()
    }
}