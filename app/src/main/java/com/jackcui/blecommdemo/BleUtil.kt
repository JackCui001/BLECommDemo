package com.jackcui.blecommdemo

import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * 蓝牙MAC地址工具类
 * 此类用于解析可能为空的蓝牙名称，从原始数据包中解析名称
 */

class BleUtil {
    class BleAdvertisedData(val uuids: List<UUID>, val name: String)

    companion object {
        private val TAG: String = "BleUtil"

        fun parseAdvertisedData(advertisedData: ByteArray?): BleAdvertisedData {
            val uuids: MutableList<UUID> = ArrayList()
            var name = ""
            if (advertisedData == null) {
                return BleAdvertisedData(uuids, name)
            }

            val buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN)
            while (buffer.remaining() > 2) {
                var length = buffer.get()
                if (length.toInt() == 0) {
                    break
                }

                val type = buffer.get()
                when (type.toInt()) {
                    0x02, 0x03 -> {
                        while (length >= 2) {
                            uuids.add(
                                UUID.fromString(
                                    String.format(
                                        "%08x-0000-1000-8000-00805f9b34fb", buffer.getShort()
                                    )
                                )
                            )
                            length = (length - 2).toByte()
                        }
                    }

                    0x06, 0x07 -> {
                        while (length >= 16) {
                            val lsb = buffer.getLong()
                            val msb = buffer.getLong()
                            uuids.add(UUID(msb, lsb))
                            length = (length - 16).toByte()
                        }
                    }

                    0x09 -> {
                        val nameBytes = ByteArray(length - 1)
                        buffer[nameBytes]
                        try {
                            name = String(nameBytes, charset("utf-8"))
                        } catch (e: UnsupportedEncodingException) {
                            e.printStackTrace()
                        }
                    }

                    else -> buffer.position(buffer.position() + length - 1)
                }
            }
            return BleAdvertisedData(uuids, name)
        }
    }
}