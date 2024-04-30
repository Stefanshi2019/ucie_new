package edu.berkeley.cs.ucie.digital
package sideband

import chisel3._
import chisel3.util._

// This module converts a message into SidebandMessageHeader() type
// to be sent out upon FSM request 
class SidebandMessageSender extends Module {
    
    val io = IO(
        new SbMsgSubIO()
    )
   
    val srcid = WireInit(SourceID.DieToDieAdapter)

    val rsvd_00 = WireInit(0.U(2.W))
    val rsvd_01 = WireInit(0.U(5.W))
    // val msgCode = WireInit(0.U(8.W))
    val rsvd_02 = WireInit(0.U(9.W))
    // val opcode = WireInit(0.U(5.W)) 
    val dp = WireInit(0.U(1.W))
    val cp = WireInit(0.U(1.W))
    val rsvd_10 = WireInit(0.U(3.W))
    val dstid = WireInit(0.U(3.W))
    val data0 = WireInit(0.U(32.W))
    val data1 = WireInit(0.U(32.W))
    
    val bits_bits_reg = WireInit(0.U(128.W))
    val bits_valid_reg = WireInit(false.B)

    bits_bits_reg  := Cat(
        srcid.asUInt, 
        rsvd_00.asUInt,
        rsvd_01.asUInt,
        io.msgCode.asUInt,
        rsvd_02.asUInt,
        io.opcode.asUInt,
        dp.asUInt,
        cp.asUInt,
        rsvd_10.asUInt,
        dstid.asUInt,
        io.msgInfo.asUInt,
        io.msgSubCode.asUInt,
        data0.asUInt,
        data1.asUInt
    )
    // bits_valid_reg := io.handshake.valid
    val fifo = Module(new Queue(UInt(128.W), 8))
    fifo.io.enq.valid := io.handshake.valid
    fifo.io.enq.bits := bits_bits_reg 
    io.handshake.ready := fifo.io.enq.ready 

    fifo.io.deq <> io.bits


    // val fifo = Module(new )
    // bits no back pressure allowed, only valid and bits
    // io.bits.bits := bits_bits_reg
    // io.bits.valid := bits_valid_reg
    // io.handshake.ready := true.B // always enable for now
}