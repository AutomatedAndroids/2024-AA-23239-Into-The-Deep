package com.acmerobotics.roadrunner.ftc.pinpoint

import com.acmerobotics.roadrunner.ftc.Encoder
import com.acmerobotics.roadrunner.ftc.PositionVelocityPair
import com.qualcomm.hardware.lynx.LynxI2cDeviceSynch
import com.qualcomm.robotcore.hardware.DcMotorController
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer
import com.qualcomm.robotcore.hardware.I2cAddr
import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice
import com.qualcomm.robotcore.hardware.I2cDeviceSynchSimple
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType
import com.qualcomm.robotcore.util.TypeConversion
import com.qualcomm.robotcore.util.TypeConversion.byteArrayToInt
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import com.qualcomm.robotcore.hardware.DcMotor;


class PinpointEncoder(private val gbpd: GoBildaPinpointDriver, public val Ydirection: Boolean, private val reserved: Boolean, private val anyDummyMotor: DcMotor) :
Encoder {
    override var direction: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD

    override fun getPositionAndVelocity(): PositionVelocityPair {
        val pos: Double
        val vel: Double
        val rawPos: Double
        if (Ydirection) {
            pos = gbpd.posY;
            vel = gbpd.velY;
            rawPos = gbpd.encoderY.toDouble();
        } else {
            pos = gbpd.posX;
            vel = gbpd.velX;
            rawPos = gbpd.encoderX.toDouble();
        }
        return PositionVelocityPair(pos,vel,rawPos,vel);
    }

    override val controller: DcMotorController
        get() = anyDummyMotor.controller

    var addedToCompanions = false
        set(value) {
            field = value || field
        }

    init {
        if (!addedToCompanions) {
            companions.add(companions.size+1, Pair(this, PinpointEncoder(gbpd, !Ydirection, false, anyDummyMotor)))
        }
    }

    companion object {
        val companions: MutableList<Pair<PinpointEncoder,PinpointEncoder>> = mutableListOf()

        fun getCompanion(key: PinpointEncoder): PinpointEncoder {
            var i = 0;
            while (true) {
                if (companions[i].first.equals(key)) {
                    return companions[i].second
                }
                if (companions[i].second.equals(key)) {
                    return companions[i].first
                }
                i++;
            }
        }
    }
}

@I2cDeviceType
@DeviceProperties(
    name = "goBILDA® Pinpoint Odometry Computer",
    xmlTag = "goBILDAPinpoint",
    description = "goBILDA® Pinpoint Odometry Computer (IMU Sensor Fusion for 2 Wheel Odometry)"
)
class GoBildaPinpointDriver(deviceClient: I2cDeviceSynchSimple?, deviceClientIsOwned: Boolean) :
    I2cDeviceSynchDevice<I2cDeviceSynchSimple?>(deviceClient, deviceClientIsOwned) {
    private var deviceStatus: Int = 0

    /**
     * Checks the Odometry Computer's most recent loop time.<br></br><br></br>
     * If values less than 500, or more than 1100 are commonly seen here, there may be something wrong with your device. Please reach out to tech@gobilda.com
     * @return loop time in microseconds (1/1,000,000 seconds)
     */
    var loopTime: Int = 0
        private set

    /**
     * @return the raw value of the X (forward) encoder in ticks
     */
    var encoderX: Int = 0
        private set

    /**
     * @return the raw value of the Y (strafe) encoder in ticks
     */
    var encoderY: Int = 0
        private set
    private var xPosition: Float = 0f
    private var yPosition: Float = 0f
    private var hOrientation: Float = 0f
    private var xVelocity: Float = 0f
    private var yVelocity: Float = 0f
    private var hVelocity: Float = 0f

    init {
        this.deviceClient!!.i2cAddress = I2cAddr.create7bit(DEFAULT_ADDRESS.toInt())
        super.registerArmingStateCallback(false)
    }


    override fun getManufacturer(): Manufacturer {
        return Manufacturer.Other
    }

    @Synchronized
    override fun doInitialize(): Boolean {
        ((deviceClient) as LynxI2cDeviceSynch).setBusSpeed(LynxI2cDeviceSynch.BusSpeed.FAST_400K)
        return true
    }

    override fun getDeviceName(): String {
        return "goBILDA® Pinpoint Odometry Computer"
    }


    //Register map of the i2c device
    private enum class Register(val bVal: Int) {
        DEVICE_ID(1),
        DEVICE_VERSION(2),
        DEVICE_STATUS(3),
        DEVICE_CONTROL(4),
        LOOP_TIME(5),
        X_ENCODER_VALUE(6),
        Y_ENCODER_VALUE(7),
        X_POSITION(8),
        Y_POSITION(9),
        H_ORIENTATION(10),
        X_VELOCITY(11),
        Y_VELOCITY(12),
        H_VELOCITY(13),
        MM_PER_TICK(14),
        X_POD_OFFSET(15),
        Y_POD_OFFSET(16),
        YAW_SCALAR(17),
        BULK_READ(18)
    }

    //Device Status enum that captures the current fault condition of the device
    enum class DeviceStatus(val status: Int) {
        NOT_READY(0),
        READY(1),
        CALIBRATING(1 shl 1),
        FAULT_X_POD_NOT_DETECTED(1 shl 2),
        FAULT_Y_POD_NOT_DETECTED(1 shl 3),
        FAULT_NO_PODS_DETECTED(1 shl 2 or (1 shl 3)),
        FAULT_IMU_RUNAWAY(1 shl 4)
    }

    //enum that captures the direction the encoders are set to
    enum class EncoderDirection {
        FORWARD,
        REVERSED
    }

    //enum that captures the kind of goBILDA odometry pods, if goBILDA pods are used
    enum class GoBildaOdometryPods {
        goBILDA_SWINGARM_POD,
        goBILDA_4_BAR_POD
    }

    //enum that captures a limited scope of read data. More options may be added in future update
    enum class readData {
        ONLY_UPDATE_HEADING,
    }


    /** Writes an int to the i2c device
     * @param reg the register to write the int to
     * @param i the integer to write to the register
     */
    private fun writeInt(reg: Register, i: Int) {
        deviceClient!!.write(reg.bVal, TypeConversion.intToByteArray(i, ByteOrder.LITTLE_ENDIAN))
    }

    /**
     * Reads an int from a register of the i2c device
     * @param reg the register to read from
     * @return returns an int that contains the value stored in the read register
     */
    private fun readInt(reg: Register): Int {
        return byteArrayToInt(deviceClient!!.read(reg.bVal, 4), ByteOrder.LITTLE_ENDIAN)
    }

    /**
     * Converts a byte array to a float value
     * @param byteArray byte array to transform
     * @param byteOrder order of byte array to convert
     * @return the float value stored by the byte array
     */
    private fun byteArrayToFloat(byteArray: ByteArray, byteOrder: ByteOrder): Float {
        return ByteBuffer.wrap(byteArray).order(byteOrder).getFloat()
    }

    /**
     * Reads a float from a register
     * @param reg the register to read
     * @return the float value stored in that register
     */
    private fun readFloat(reg: Register): Float {
        return byteArrayToFloat(deviceClient!!.read(reg.bVal, 4), ByteOrder.LITTLE_ENDIAN)
    }


    /**
     * Converts a float to a byte array
     * @param value the float array to convert
     * @return the byte array converted from the float
     */
    private fun floatToByteArray(value: Float, byteOrder: ByteOrder): ByteArray {
        return ByteBuffer.allocate(4).order(byteOrder).putFloat(value).array()
    }

    /**
     * Writes a byte array to a register on the i2c device
     * @param reg the register to write to
     * @param bytes the byte array to write
     */
    private fun writeByteArray(reg: Register, bytes: ByteArray) {
        deviceClient!!.write(reg.bVal, bytes)
    }

    /**
     * Writes a float to a register on the i2c device
     * @param reg the register to write to
     * @param f the float to write
     */
    private fun writeFloat(reg: Register, f: Float) {
        val bytes: ByteArray =
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(f).array()
        deviceClient!!.write(reg.bVal, bytes)
    }

    /**
     * Looks up the DeviceStatus enum corresponding with an int value
     * @param s int to lookup
     * @return the Odometry Computer state
     */
    private fun lookupStatus(s: Int): DeviceStatus {
        if ((s and DeviceStatus.CALIBRATING.status) != 0) {
            return DeviceStatus.CALIBRATING
        }
        val xPodDetected: Boolean = (s and DeviceStatus.FAULT_X_POD_NOT_DETECTED.status) == 0
        val yPodDetected: Boolean = (s and DeviceStatus.FAULT_Y_POD_NOT_DETECTED.status) == 0

        if (!xPodDetected && !yPodDetected) {
            return DeviceStatus.FAULT_NO_PODS_DETECTED
        }
        if (!xPodDetected) {
            return DeviceStatus.FAULT_X_POD_NOT_DETECTED
        }
        if (!yPodDetected) {
            return DeviceStatus.FAULT_Y_POD_NOT_DETECTED
        }
        if ((s and DeviceStatus.FAULT_IMU_RUNAWAY.status) != 0) {
            return DeviceStatus.FAULT_IMU_RUNAWAY
        }
        if ((s and DeviceStatus.READY.status) != 0) {
            return DeviceStatus.READY
        } else {
            return DeviceStatus.NOT_READY
        }
    }

    /**
     * Call this once per loop to read new data from the Odometry Computer. Data will only update once this is called.
     */
    fun update() {
        val bArr: ByteArray = deviceClient!!.read(Register.BULK_READ.bVal, 40)
        deviceStatus = byteArrayToInt(Arrays.copyOfRange(bArr, 0, 4), ByteOrder.LITTLE_ENDIAN)
        loopTime = byteArrayToInt(Arrays.copyOfRange(bArr, 4, 8), ByteOrder.LITTLE_ENDIAN)
        encoderX = byteArrayToInt(Arrays.copyOfRange(bArr, 8, 12), ByteOrder.LITTLE_ENDIAN)
        encoderY = byteArrayToInt(Arrays.copyOfRange(bArr, 12, 16), ByteOrder.LITTLE_ENDIAN)
        xPosition = byteArrayToFloat(Arrays.copyOfRange(bArr, 16, 20), ByteOrder.LITTLE_ENDIAN)
        yPosition = byteArrayToFloat(Arrays.copyOfRange(bArr, 20, 24), ByteOrder.LITTLE_ENDIAN)
        hOrientation = byteArrayToFloat(Arrays.copyOfRange(bArr, 24, 28), ByteOrder.LITTLE_ENDIAN)
        xVelocity = byteArrayToFloat(Arrays.copyOfRange(bArr, 28, 32), ByteOrder.LITTLE_ENDIAN)
        yVelocity = byteArrayToFloat(Arrays.copyOfRange(bArr, 32, 36), ByteOrder.LITTLE_ENDIAN)
        hVelocity = byteArrayToFloat(Arrays.copyOfRange(bArr, 36, 40), ByteOrder.LITTLE_ENDIAN)
    }

    /**
     * Call this once per loop to read new data from the Odometry Computer. This is an override of the update() function
     * which allows a narrower range of data to be read from the device for faster read times. Currently ONLY_UPDATE_HEADING
     * is supported.
     * @param data GoBildaPinpointDriver.readData.ONLY_UPDATE_HEADING
     */
    fun update(data: readData) {
        if (data == readData.ONLY_UPDATE_HEADING) {
            hOrientation = byteArrayToFloat(
                deviceClient!!.read(Register.H_ORIENTATION.bVal, 4),
                ByteOrder.LITTLE_ENDIAN
            )
        }
    }

    /**
     * Sets the odometry pod positions relative to the point that the odometry computer tracks around.<br></br><br></br>
     * The most common tracking position is the center of the robot. <br></br> <br></br>
     * The X pod offset refers to how far sideways (in mm) from the tracking point the X (forward) odometry pod is. Left of the center is a positive number, right of center is a negative number. <br></br>
     * the Y pod offset refers to how far forwards (in mm) from the tracking point the Y (strafe) odometry pod is. forward of center is a positive number, backwards is a negative number.<br></br>
     * @param xOffset how sideways from the center of the robot is the X (forward) pod? Left increases
     * @param yOffset how far forward from the center of the robot is the Y (Strafe) pod? forward increases
     */
    fun setOffsets(xOffset: Double, yOffset: Double) {
        writeFloat(Register.X_POD_OFFSET, xOffset.toFloat())
        writeFloat(Register.Y_POD_OFFSET, yOffset.toFloat())
    }

    /**
     * Recalibrates the Odometry Computer's internal IMU. <br></br><br></br>
     * ** Robot MUST be stationary ** <br></br><br></br>
     * Device takes a large number of samples, and uses those as the gyroscope zero-offset. This takes approximately 0.25 seconds.
     */
    fun recalibrateIMU() {
        writeInt(Register.DEVICE_CONTROL, 1 shl 0)
    }

    /**
     * Resets the current position to 0,0,0 and recalibrates the Odometry Computer's internal IMU. <br></br><br></br>
     * ** Robot MUST be stationary ** <br></br><br></br>
     * Device takes a large number of samples, and uses those as the gyroscope zero-offset. This takes approximately 0.25 seconds.
     */
    fun resetPosAndIMU() {
        writeInt(Register.DEVICE_CONTROL, 1 shl 1)
    }

    /**
     * Can reverse the direction of each encoder.
     * @param xEncoder FORWARD or REVERSED, X (forward) pod should increase when the robot is moving forward
     * @param yEncoder FORWARD or REVERSED, Y (strafe) pod should increase when the robot is moving left
     */
    fun setEncoderDirections(xEncoder: EncoderDirection, yEncoder: EncoderDirection) {
        if (xEncoder == EncoderDirection.FORWARD) {
            writeInt(Register.DEVICE_CONTROL, 1 shl 5)
        }
        if (xEncoder == EncoderDirection.REVERSED) {
            writeInt(Register.DEVICE_CONTROL, 1 shl 4)
        }

        if (yEncoder == EncoderDirection.FORWARD) {
            writeInt(Register.DEVICE_CONTROL, 1 shl 3)
        }
        if (yEncoder == EncoderDirection.REVERSED) {
            writeInt(Register.DEVICE_CONTROL, 1 shl 2)
        }
    }

    /**
     * If you're using goBILDA odometry pods, the ticks-per-mm values are stored here for easy access.<br></br><br></br>
     * @param pods goBILDA_SWINGARM_POD or goBILDA_4_BAR_POD
     */
    fun setEncoderResolution(pods: GoBildaOdometryPods) {
        if (pods == GoBildaOdometryPods.goBILDA_SWINGARM_POD) {
            writeByteArray(
                Register.MM_PER_TICK,
                (floatToByteArray(goBILDA_SWINGARM_POD, ByteOrder.LITTLE_ENDIAN))
            )
        }
        if (pods == GoBildaOdometryPods.goBILDA_4_BAR_POD) {
            writeByteArray(
                Register.MM_PER_TICK,
                (floatToByteArray(goBILDA_4_BAR_POD, ByteOrder.LITTLE_ENDIAN))
            )
        }
    }

    /**
     * Sets the encoder resolution in ticks per mm of the odometry pods. <br></br>
     * You can find this number by dividing the counts-per-revolution of your encoder by the circumference of the wheel.
     * @param ticks_per_mm should be somewhere between 10 ticks/mm and 100 ticks/mm a goBILDA Swingarm pod is ~13.26291192
     */
    fun setEncoderResolution(ticks_per_mm: Double) {
        writeByteArray(
            Register.MM_PER_TICK,
            (floatToByteArray(ticks_per_mm.toFloat(), ByteOrder.LITTLE_ENDIAN))
        )
    }

    /**
     * Tuning this value should be unnecessary.<br></br>
     * The goBILDA Odometry Computer has a per-device tuned yaw offset already applied when you receive it.<br></br><br></br>
     * This is a scalar that is applied to the gyro's yaw value. Increasing it will mean it will report more than one degree for every degree the sensor fusion algorithm measures. <br></br><br></br>
     * You can tune this variable by rotating the robot a large amount (10 full turns is a good starting place) and comparing the amount that the robot rotated to the amount measured.
     * Rotating the robot exactly 10 times should measure 3600°. If it measures more or less, divide moved amount by the measured amount and apply that value to the Yaw Offset.<br></br><br></br>
     * If you find that to get an accurate heading number you need to apply a scalar of more than 1.05, or less than 0.95, your device may be bad. Please reach out to tech@gobilda.com
     * @param yawOffset A scalar for the robot's heading.
     */
    fun setYawScalar(yawOffset: Double) {
        writeByteArray(
            Register.YAW_SCALAR,
            (floatToByteArray(yawOffset.toFloat(), ByteOrder.LITTLE_ENDIAN))
        )
    }

    /**
     * Send a position that the Pinpoint should use to track your robot relative to. You can use this to
     * update the estimated position of your robot with new external sensor data, or to run a robot
     * in field coordinates. <br></br><br></br>
     * This overrides the current position. <br></br><br></br>
     * **Using this feature to track your robot's position in field coordinates:** <br></br>
     * When you start your code, send a Pose2D that describes the starting position on the field of your robot. <br></br>
     * Say you're on the red alliance, your robot is against the wall and closer to the audience side,
     * and the front of your robot is pointing towards the center of the field.
     * You can send a setPosition with something like -600mm x, -1200mm Y, and 90 degrees. The pinpoint would then always
     * keep track of how far away from the center of the field you are. <br></br><br></br>
     * **Using this feature to update your position with additional sensors: **<br></br>
     * Some robots have a secondary way to locate their robot on the field. This is commonly
     * Apriltag localization in FTC, but it can also be something like a distance sensor.
     * Often these external sensors are absolute (meaning they measure something about the field)
     * so their data is very accurate. But they can be slower to read, or you may need to be in a very specific
     * position on the field to use them. In that case, spend most of your time relying on the Pinpoint
     * to determine your location. Then when you pull a new position from your secondary sensor,
     * send a setPosition command with the new position. The Pinpoint will then track your movement
     * relative to that new, more accurate position.
     * @param pos a Pose2D describing the robot's new position.
     */
    fun setPosition(pos: Pose2D): Pose2D {
        writeByteArray(
            Register.X_POSITION,
            (floatToByteArray(pos.getX(DistanceUnit.MM).toFloat(), ByteOrder.LITTLE_ENDIAN))
        )
        writeByteArray(
            Register.Y_POSITION,
            (floatToByteArray(pos.getY(DistanceUnit.MM).toFloat(), ByteOrder.LITTLE_ENDIAN))
        )
        writeByteArray(
            Register.H_ORIENTATION,
            (floatToByteArray(pos.getHeading(AngleUnit.RADIANS).toFloat(), ByteOrder.LITTLE_ENDIAN))
        )
        return pos
    }

    val deviceID: Int
        /**
         * Checks the deviceID of the Odometry Computer. Should return 1.
         * @return 1 if device is functional.
         */
        get() = readInt(Register.DEVICE_ID)

    val deviceVersion: Int
        /**
         * @return the firmware version of the Odometry Computer
         */
        get() = readInt(Register.DEVICE_VERSION)

    val yawScalar: Float
        get() = readFloat(Register.YAW_SCALAR)

    /**
     * Device Status stores any faults the Odometry Computer may be experiencing. These faults include:
     * @return one of the following states:<br></br>
     * NOT_READY - The device is currently powering up. And has not initialized yet. RED LED<br></br>
     * READY - The device is currently functioning as normal. GREEN LED<br></br>
     * CALIBRATING - The device is currently recalibrating the gyro. RED LED<br></br>
     * FAULT_NO_PODS_DETECTED - the device does not detect any pods plugged in. PURPLE LED <br></br>
     * FAULT_X_POD_NOT_DETECTED - The device does not detect an X pod plugged in. BLUE LED <br></br>
     * FAULT_Y_POD_NOT_DETECTED - The device does not detect a Y pod plugged in. ORANGE LED <br></br>
     */
    fun getDeviceStatus(): DeviceStatus {
        return lookupStatus(deviceStatus)
    }

    val frequency: Double
        /**
         * Checks the Odometry Computer's most recent loop frequency.<br></br><br></br>
         * If values less than 900, or more than 2000 are commonly seen here, there may be something wrong with your device. Please reach out to tech@gobilda.com
         * @return Pinpoint Frequency in Hz (loops per second),
         */
        get() {
            if (loopTime != 0) {
                return 1000000.0 / loopTime
            } else {
                return 0.0
            }
        }

    val posX: Double
        /**
         * @return the estimated X (forward) position of the robot in mm
         */
        get() = xPosition.toDouble()

    val posY: Double
        /**
         * @return the estimated Y (Strafe) position of the robot in mm
         */
        get() = yPosition.toDouble()

    val heading: Double
        /**
         * @return the estimated H (heading) position of the robot in Radians
         */
        get() = hOrientation.toDouble()

    val velX: Double
        /**
         * @return the estimated X (forward) velocity of the robot in mm/sec
         */
        get() = xVelocity.toDouble()

    val velY: Double
        /**
         * @return the estimated Y (strafe) velocity of the robot in mm/sec
         */
        get() = yVelocity.toDouble()

    val headingVelocity: Double
        /**
         * @return the estimated H (heading) velocity of the robot in radians/sec
         */
        get() = hVelocity.toDouble()

    val xOffset: Float
        /**
         * ** This uses its own I2C read, avoid calling this every loop. **
         * @return the user-set offset for the X (forward) pod
         */
        get() = readFloat(Register.X_POD_OFFSET)

    val yOffset: Float
        /**
         * ** This uses its own I2C read, avoid calling this every loop. **
         * @return the user-set offset for the Y (strafe) pod
         */
        get() = readFloat(Register.Y_POD_OFFSET)

    val position: Pose2D
        /**
         * @return a Pose2D containing the estimated position of the robot
         */
        get() = Pose2D(
            DistanceUnit.MM,
            xPosition.toDouble(),
            yPosition.toDouble(),
            AngleUnit.RADIANS,
            hOrientation.toDouble()
        )


    val velocity: Pose2D
        /**
         * @return a Pose2D containing the estimated velocity of the robot, velocity is unit per second
         */
        get() {
            return Pose2D(
                DistanceUnit.MM,
                xVelocity.toDouble(),
                yVelocity.toDouble(),
                AngleUnit.RADIANS,
                hVelocity.toDouble()
            )
        }


    companion object {
        private const val goBILDA_SWINGARM_POD: Float =
            13.26291192f //ticks-per-mm for the goBILDA Swingarm Pod
        private const val goBILDA_4_BAR_POD: Float =
            19.89436789f //ticks-per-mm for the goBILDA 4-Bar Pod

        //i2c address of the device
        const val DEFAULT_ADDRESS: Byte = 0x31
    }
}