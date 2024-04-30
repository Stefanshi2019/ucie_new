package edu.berkeley.cs.ucie.digital
package top 

import chisel3._ 
import chisel3.util._ 
import interfaces._
import sideband._
import afe._ 
import d2dadaptor._ 
import logphy._ 


class TopIO (
    afeParams: AfeParams,
    rdiParams: RdiParams,
) extends Bundle {
    val rdi_send = Flipped(new Rdi(rdiParams))
    val rdi_receive = Flipped(new Rdi(rdiParams))
    val rdi_sb_send = new SbMsgIO()
    val rdi_sb_receive = new SbMsgIO()
}

class Top (
    afeParams: AfeParams,
    rdiParams: RdiParams,
) extends Module {
    val io = IO(
        new TopIO(afeParams, rdiParams)
    )
    val logphy_send = Module (new LogicalPhy(afeParams, rdiParams))
    val logphy_receive = Module(new LogicalPhy(afeParams, rdiParams))
    // val io = IO(new Bundle {
    //     val rdi = Flipped(new Rdi(rdiParams))
    //     val mbAfe = new MainbandAfeIo(afeParams) // do afe later
    //     val sbAfe = new SidebandAfeIo(afeParams)
    //     val rdiSb = new SbMsgIO() // contains tx and rx
    // // })
    // logphy_receive.io.rdi.lpData.valid := false.B
    // // io.rdi_receive.lpData.bits := 0.U 
    // // io.rdi_receive.lp_clk_ack := false.B
    // logphy_receive.io.rdi.lpData.irdy := false.B
    // // io.rdi_receive.lpData.valid := false.B
    // logphy_receive.io.rdi.lp_state_req := PhyStateReq.nop
    // // io.rdi_receive.lpData.irdy := false.B
    // // io.rdi_receive.lp_stallack := false.B
    // logphy_receive.io.rdi.lp_clk_ack := false.B
    // logphy_receive.io.rdi.lp_wake_req := false.B
    // // io.rdi_receive.lp_state_req := PhyStateReq.nop
    // logphy_receive.io.rdi.lp_stallack := false.B
    // logphy_receive.io.rdi.lp_linkerror := false.B
    // // io.rdi_receive.lp_linkerror := false.B
    // logphy_receive.io.rdi.lpData.bits := 0.U
    // io.rdi_receive.lp_wake_req := false.B


    io.rdi_send <> logphy_send.io.rdi
    io.rdi_receive <> logphy_receive.io.rdi 

    logphy_send.io.mbAfe.txData <> logphy_receive.io.mbAfe.rxData 
    logphy_send.io.sbAfe.txData <> logphy_receive.io.sbAfe.rxData 
    logphy_send.io.mbAfe.rxData <> logphy_receive.io.mbAfe.txData 
    logphy_send.io.sbAfe.rxData <> logphy_receive.io.sbAfe.txData
    io.rdi_sb_send.tx <> logphy_send.io.rdiSb.tx 
    io.rdi_sb_send.rx <> logphy_send.io.rdiSb.rx 

    io.rdi_sb_receive.tx <> logphy_receive.io.rdiSb.tx
    io.rdi_sb_receive.rx <> logphy_receive.io.rdiSb.rx
    
}