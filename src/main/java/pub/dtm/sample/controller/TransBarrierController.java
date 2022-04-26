package pub.dtm.sample.controller;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pub.dtm.client.barrier.BranchBarrier;
import pub.dtm.client.constant.Constants;
import pub.dtm.client.model.responses.DtmResponse;
import pub.dtm.sample.param.TransReq;
import pub.dtm.sample.utils.DataSourceUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@RestController
public class TransBarrierController {
    private static final Logger LOG = LogManager.getLogger(TransBarrierController.class);

    @Autowired
    private DataSourceUtil dataSourceUtil;



    @RequestMapping("barrierTransOutTry")
    public Object TransOutTry(HttpServletRequest request) throws Exception {

        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        LOG.info("barrierTransOutTry branchBarrier:{}", branchBarrier);

        TransReq transReq = extracted(request);
        Connection connection = dataSourceUtil.getConnecion();
        branchBarrier.call(connection, (barrier) -> {
            System.out.println("用户: +" + transReq.getUserId() + ",转出" + Math.abs(transReq.getAmount()) + "元准备");
            this.adjustTrading(connection, transReq);
        });
        connection.close();
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }


    @RequestMapping("barrierTransOutConfirm")
    public Object TransOutConfirm(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        LOG.info("barrierTransOutConfirm branchBarrier:{}", branchBarrier);
        Connection connection = dataSourceUtil.getConnecion();
        TransReq transReq = extracted(request);
        branchBarrier.call(connection, (barrier) -> {
            System.out.println("用户: +" + transReq.getUserId() + ",转出" + Math.abs(transReq.getAmount()) + "元提交");
            adjustBalance(connection, transReq);
        });
        connection.close();
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }

    @RequestMapping("barrierTransOutCancel")
    public Object TransOutCancel(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        LOG.info("barrierTransOutCancel branchBarrier:{}", branchBarrier);
        TransReq transReq = extracted(request);
        Connection connection = dataSourceUtil.getConnecion();
        branchBarrier.call(connection, (barrier) -> {
            System.out.println("用户: +" + transReq.getUserId() + ",转出" + Math.abs(transReq.getAmount()) + "元回滚");
            this.adjustTrading(connection, transReq);
        });
        connection.close();
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }

    @RequestMapping("barrierTransInTry")
    public Object TransInTry(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        LOG.info("barrierTransInTry branchBarrier:{}", branchBarrier);
        Connection connection = dataSourceUtil.getConnecion();
        TransReq transReq = extracted(request);
        branchBarrier.call(connection, (barrier) -> {
            System.out.println("用户: +" + transReq.getUserId() + ",转入" + Math.abs(transReq.getAmount()) + "元准备");
            this.adjustTrading(connection, transReq);
        });
        connection.close();
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }

    @RequestMapping("barrierTransInConfirm")
    public Object TransInConfirm(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        LOG.info("barrierTransInConfirm TransInCancel branchBarrier:{}", branchBarrier);
        Connection connection = dataSourceUtil.getConnecion();
        TransReq transReq = extracted(request);
        branchBarrier.call(connection, (barrier) -> {
            System.out.println("用户: +" + transReq.getUserId() + ",转入" + Math.abs(transReq.getAmount()) + "元提交");
            adjustBalance(connection, transReq);
        });
        connection.close();
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }

    @RequestMapping("barrierTransInCancel")
    public Object TransInCancel(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        LOG.info("barrierTransInCancel branchBarrier:{}", branchBarrier);
        Connection connection = dataSourceUtil.getConnecion();
        TransReq transReq = extracted(request);
        branchBarrier.call(connection, (barrier) -> {
            System.out.println("用户: +" + transReq.getUserId() + ",转入" + Math.abs(transReq.getAmount()) + "回滚");
            this.adjustTrading(connection, transReq);
        });
        connection.close();
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }

    /**
     * 提取body中参数
     *
     * @param request
     * @return
     * @throws IOException
     */
    private TransReq extracted(HttpServletRequest request) throws IOException {
        byte[] bytes = StreamUtils.copyToByteArray(request.getInputStream());
        return new Gson().fromJson(new String(bytes), TransReq.class);
    }

    /**
     * 更新交易金额
     *
     * @param connection
     * @param transReq
     * @throws SQLException
     */
    public void adjustTrading(Connection connection, TransReq transReq) throws Exception {
        String sql = "update dtm_busi.user_account set trading_balance=trading_balance+?"
                + " where user_id=? and trading_balance + ? + balance >= 0";
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, transReq.getAmount());
            preparedStatement.setInt(2, transReq.getUserId());
            preparedStatement.setInt(3, transReq.getAmount());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("交易金额更新成功");
            } else {
                throw new Exception("交易失败");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }

    }

    /**
     * 更新余额
     */
    public void adjustBalance(Connection connection, TransReq transReq) throws SQLException {
        PreparedStatement preparedStatement = null;
        try {
            String sql = "update dtm_busi.user_account set trading_balance=trading_balance-?,balance=balance+? where user_id=?";
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, transReq.getAmount());
            preparedStatement.setInt(2, transReq.getAmount());
            preparedStatement.setInt(3, transReq.getUserId());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("余额更新成功");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
    }
}
