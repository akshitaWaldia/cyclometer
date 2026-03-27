package com.cyclecomp.app.data.ble

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BleCharacteristicParsersTest : FunSpec({

    context("parseHeartRate") {
        test("parses UINT8 format (flag bit 0 = 0)") {
            // flags=0x00 (UINT8), HR=72
            val data = byteArrayOf(0x00, 72)
            BleCharacteristicParsers.parseHeartRate(data) shouldBe 72
        }

        test("parses UINT16 format (flag bit 0 = 1)") {
            // flags=0x01 (UINT16), HR=300 = 0x012C (little-endian: 0x2C, 0x01)
            val data = byteArrayOf(0x01, 0x2C, 0x01)
            BleCharacteristicParsers.parseHeartRate(data) shouldBe 300
        }

        test("parses UINT8 with value 0") {
            val data = byteArrayOf(0x00, 0x00)
            BleCharacteristicParsers.parseHeartRate(data) shouldBe 0
        }

        test("parses UINT8 with max single-byte value 255") {
            val data = byteArrayOf(0x00, 0xFF.toByte())
            BleCharacteristicParsers.parseHeartRate(data) shouldBe 255
        }

        test("returns null for empty data") {
            BleCharacteristicParsers.parseHeartRate(byteArrayOf()).shouldBeNull()
        }

        test("returns null for UINT8 with insufficient bytes") {
            val data = byteArrayOf(0x00) // missing HR byte
            BleCharacteristicParsers.parseHeartRate(data).shouldBeNull()
        }

        test("returns null for UINT16 with insufficient bytes") {
            val data = byteArrayOf(0x01, 0x48) // missing second HR byte
            BleCharacteristicParsers.parseHeartRate(data).shouldBeNull()
        }
    }

    context("parseCscMeasurement") {
        test("parses crank data without wheel data") {
            // flags=0x02 (crank present, no wheel), crankRevs=100 (0x0064), lastEvent=512 (0x0200)
            val data = byteArrayOf(
                0x02,                   // flags: crank data present
                0x64, 0x00,             // cumulative crank revolutions = 100
                0x00, 0x02              // last crank event time = 512
            )
            val result = BleCharacteristicParsers.parseCscMeasurement(data)
            result.shouldNotBeNull()
            result.cumulativeCrankRevolutions shouldBe 100
            result.lastCrankEventTime shouldBe 512
        }

        test("parses crank data with wheel data present") {
            // flags=0x03 (both wheel and crank present)
            // wheel data: 4 bytes revs + 2 bytes event time = 6 bytes
            // then crank data
            val data = byteArrayOf(
                0x03,                   // flags: wheel + crank present
                0x00, 0x00, 0x00, 0x00, // cumulative wheel revolutions (ignored)
                0x00, 0x00,             // last wheel event time (ignored)
                0x32, 0x00,             // cumulative crank revolutions = 50
                0x00, 0x04              // last crank event time = 1024
            )
            val result = BleCharacteristicParsers.parseCscMeasurement(data)
            result.shouldNotBeNull()
            result.cumulativeCrankRevolutions shouldBe 50
            result.lastCrankEventTime shouldBe 1024
        }

        test("returns null when crank data not present") {
            // flags=0x00 (neither wheel nor crank)
            val data = byteArrayOf(0x00)
            BleCharacteristicParsers.parseCscMeasurement(data).shouldBeNull()
        }

        test("returns null for empty data") {
            BleCharacteristicParsers.parseCscMeasurement(byteArrayOf()).shouldBeNull()
        }

        test("returns null when crank data flag set but insufficient bytes") {
            val data = byteArrayOf(0x02, 0x64) // only 1 byte of crank data
            BleCharacteristicParsers.parseCscMeasurement(data).shouldBeNull()
        }
    }

    context("deriveCadenceRpm") {
        test("calculates RPM from two consecutive measurements") {
            val prev = BleCharacteristicParsers.CscMeasurement(
                cumulativeCrankRevolutions = 100,
                lastCrankEventTime = 1024 // 1 second
            )
            val curr = BleCharacteristicParsers.CscMeasurement(
                cumulativeCrankRevolutions = 101,
                lastCrankEventTime = 2048 // 2 seconds
            )
            // 1 rev in 1 second = 60 RPM
            BleCharacteristicParsers.deriveCadenceRpm(prev, curr) shouldBe 60
        }

        test("handles 16-bit rollover for revolutions") {
            val prev = BleCharacteristicParsers.CscMeasurement(
                cumulativeCrankRevolutions = 65535,
                lastCrankEventTime = 0
            )
            val curr = BleCharacteristicParsers.CscMeasurement(
                cumulativeCrankRevolutions = 1,
                lastCrankEventTime = 1024
            )
            // 2 revs in 1 second = 120 RPM
            BleCharacteristicParsers.deriveCadenceRpm(prev, curr) shouldBe 120
        }

        test("handles 16-bit rollover for time") {
            val prev = BleCharacteristicParsers.CscMeasurement(
                cumulativeCrankRevolutions = 100,
                lastCrankEventTime = 65000
            )
            val curr = BleCharacteristicParsers.CscMeasurement(
                cumulativeCrankRevolutions = 102,
                lastCrankEventTime = 560 // rolled over
            )
            // deltaTime = 560 - 65000 + 65536 = 1096
            // deltaTimeSec = 1096 / 1024 ≈ 1.0703
            // RPM = (2 / 1.0703) * 60 ≈ 112
            val rpm = BleCharacteristicParsers.deriveCadenceRpm(prev, curr)
            rpm.shouldNotBeNull()
            // Allow some rounding
            (rpm in 110..114) shouldBe true
        }

        test("returns null when delta time is zero") {
            val prev = BleCharacteristicParsers.CscMeasurement(100, 1024)
            val curr = BleCharacteristicParsers.CscMeasurement(101, 1024)
            BleCharacteristicParsers.deriveCadenceRpm(prev, curr).shouldBeNull()
        }

        test("clamps RPM to reasonable range") {
            val prev = BleCharacteristicParsers.CscMeasurement(0, 0)
            val curr = BleCharacteristicParsers.CscMeasurement(1000, 1) // absurd rate
            val rpm = BleCharacteristicParsers.deriveCadenceRpm(prev, curr)
            rpm.shouldNotBeNull()
            (rpm <= 300) shouldBe true
        }
    }
})
