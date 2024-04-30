package edu.berkeley.cs.ucie.digital
package sideband

import chisel3._
import chisel3.util._

// This module turns message to be sent into a 32 bit stream
class SidebandMessageReceiver extends Module {
    val io = IO( 
        Flipped(new SbMsgSubIO())
    )

    val phaseCounter = RegInit(0.U(32.W))
    val msgHeader = new SidebandMessageHeader()

    val srcid = WireInit(SourceID.DieToDieAdapter)

    val rsvd_00 = WireInit(0.U(2.W))
    val rsvd_01 = WireInit(0.U(5.W))
    val msgCode = WireInit(MsgCode.Nop)
    val rsvd_02 = WireInit(0.U(9.W))
    val opcode = WireInit(Opcode.MessageWithoutData) 
    val dp = WireInit(0.U(1.W))
    val cp = WireInit(0.U(1.W))
    val rsvd_10 = WireInit(0.U(3.W))
    val dstid = WireInit(0.U(3.W))

    val msgInfo = WireInit(MsgInfo.RegularResponse)
    val msgSubCode = WireInit(MsgSubCode.Nak)

    val data0 = WireInit(0.U(32.W))
    val data1 = WireInit(0.U(32.W))

    
    val fifo = Module(new Queue(UInt(128.W), 8))

    fifo.io.enq <> io.bits 
    io.handshake.valid := fifo.io.deq.valid
    fifo.io.deq.ready := io.handshake.ready

    srcid := SourceID(fifo.io.deq.bits(31+32+64, 29+32+64))
    rsvd_00 := fifo.io.deq.bits(28+32+64, 27+32+64)
    rsvd_01 := fifo.io.deq.bits(26+32+64, 22+32+64)
    msgCode := MsgCode(fifo.io.deq.bits(21+32+64, 14+32+64))
    rsvd_02 := fifo.io.deq.bits(13+32+64, 5+32+64)
    opcode := Opcode(fifo.io.deq.bits(4+32+64, 0+32+64))

    dp := fifo.io.deq.bits(31+64)
    cp := fifo.io.deq.bits(30+64)
    rsvd_10 := fifo.io.deq.bits(29+64, 27+64)
    dstid := fifo.io.deq.bits(26+64, 24+64)
    msgInfo := MsgInfo(fifo.io.deq.bits(23+64, 8+64))
    msgSubCode := MsgSubCode(fifo.io.deq.bits(7+64, 0+64))
    data0 := fifo.io.deq.bits(31+32, 32)
    data1 := fifo.io.deq.bits(31, 0)

    io.msgCode := msgCode 
    io.msgInfo := msgInfo
    io.msgSubCode := msgSubCode 
    io.opcode := opcode 
    io.data0 := data0
    io.data1 := data1
    
    // io.handshake.valid := bits_valid_reg
    io.bits.ready := true.B
    
}
