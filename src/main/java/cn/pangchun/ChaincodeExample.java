package cn.pangchun;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pangchun
 */
public class ChaincodeExample extends ChaincodeBase {

    private static final Log logger = LogFactory.getLog(ChaincodeExample.class);

    /**
     * init 方法用于实例化合约时调用
     * @param stub
     * @return
     */
    @Override
    public Response init(ChaincodeStub stub) {
        try {
            logger.info("开始初始化");

            String func = stub.getFunction();
            if (!func.equals("init")) {
                return newErrorResponse("请传入init方法");
            }

            List<String> args = stub.getParameters();
            if (args.size() != 4) {
                return newErrorResponse("应传入4个参数");
            }

            // 初始化合约 设置两个账户名，并初始化两个账户中的余额
            String account1Key = args.get(0);
            int account1Value = Integer.parseInt(args.get(1));
            String account2Key = args.get(2);
            int account2Value = Integer.parseInt(args.get(3));

            // 调用API putStringState 把数据写入账本
            stub.putStringState(account1Key, args.get(1));
            stub.putStringState(account2Key, args.get(3));

            return newSuccessResponse("初始化成功");
        } catch (Throwable e) {
            return newErrorResponse(e.getMessage());
        }
    }

    /**
     * invoke 方法用于方法分发
     * @param stub
     * @return
     */
    @Override
    public Response invoke(ChaincodeStub stub) {
        try {
            logger.info("开始调用合约");
            String func = stub.getFunction();

            List<String> params = stub.getParameters();
            if (func.equals("create")) {
                return create(stub, params);
            }
            if (func.equals("update")) {
                return update(stub, params);
            }
            if (func.equals("delete")) {
                return delete(stub, params);
            }
            if (func.equals("query")) {
                return query(stub, params);
            }
            if (func.equals("transfer")) {
                return transfer(stub, params);
            }
            if (func.equals("history")) {
                return history(stub, params);
            }

            return new Response(Response.Status.INTERNAL_SERVER_ERROR, "请传入正确的方法名. 只能传其中一个: [\"create\", \"update\", \"delete\", \"query\", \"transfer\", \"history\"]", null);
        } catch (Throwable e) {
            return new Response(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage(), null);
        }
    }

    /**
     * 新建账户
     * @param stub
     * @param args
     * @return
     */
    private Response create(ChaincodeStub stub, List<String> args) {
        if (args.size() != 2) {
            return newErrorResponse("应传入2个参数");
        }

        String accountKey = args.get(0);
        int accountValue = Integer.parseInt(args.get(1));

        // 校验两个参数
        if (StrUtil.isBlank(accountKey)) {
            return newErrorResponse("账户名为空");
        }
        if (accountValue < 0) {
            return newErrorResponse("账户金额不能小于零");
        }

        // 如果链上已经存在这个账户名，说明重复了，返回错误提醒
        String sameAccountKey = stub.getStringState(accountKey);
        if (StrUtil.isNotBlank(sameAccountKey)) {
            return newErrorResponse(String.format("账户名 %s 已存在", accountKey));
        }

        // 新增账户
        stub.putStringState(accountKey, args.get(1));

        return newSuccessResponse("创建账户成功");
    }

    /**
     * 修改账余额
     * @param stub
     * @param args
     * @return
     */
    private Response update(ChaincodeStub stub, List<String> args) {
        if (args.size() != 2) {
            return newErrorResponse("应传入2个参数");
        }

        String accountKey = args.get(0);
        int accountValue = Integer.parseInt(args.get(1));

        // 校验两个参数
        if (StrUtil.isBlank(accountKey)) {
            return newErrorResponse("账户名为空");
        }
        if (accountValue < 0) {
            return newErrorResponse("账户金额不能小于零");
        }

        // 如果链上不存在这个电子凭证，返回错误提醒
        if (StrUtil.isBlank(stub.getStringState(accountKey))) {
            return newErrorResponse(String.format("账户名 %s 不存在", accountKey));
        }

        // 更新账户
        stub.putStringState(accountKey, args.get(1));

        return newSuccessResponse("修改账户金额成功");
    }

    /**
     * 删除某个账户
     * @param stub
     * @param args
     * @return
     */
    private Response delete(ChaincodeStub stub, List<String> args) {
        if (args.size() != 1) {
            return newErrorResponse("应传入1个参数");
        }
        String accountKey = args.get(0);

        // 校验参数不能为空
        if (StrUtil.isBlank(accountKey)) {
            return newErrorResponse("账户名为空");
        }

        // 删除账户
        stub.delState(accountKey);

        return newSuccessResponse("删除账户成功");
    }

    /**
     * 查询账户余额
     * @param stub
     * @param args
     * @return
     */
    private Response query(ChaincodeStub stub, List<String> args) {
        if (args.size() != 1) {
            return newErrorResponse("应传入1个参数");
        }
        String accountKey = args.get(0);

        // 校验参数
        if (StrUtil.isBlank(accountKey)) {
            return newErrorResponse("账户名为空");
        }

        // 查询存证，如果链上不存在这个电子凭证，返回错误提醒
        String accountValue = stub.getStringState(accountKey);
        if (StrUtil.isBlank(accountValue)) {
            return newErrorResponse(String.format("账户名 %s 不存在", accountKey));
        }

        return newSuccessResponse("查询账户成功", accountValue.getBytes());
    }

    /**
     * 转账
     * @param stub
     * @param args
     * @return
     */
    private Response transfer(ChaincodeStub stub, List<String> args) {
        if (args.size() != 3) {
            return newErrorResponse("应传入3个参数");
        }

        // 获取两个账户名和转账金额
        String account1Key = args.get(0);
        String account2Key = args.get(1);
        int account = Integer.parseInt(args.get(2));

        // 查询两个账户的余额
        int account1Value = Integer.parseInt(stub.getStringState(account1Key));
        int account2Value = Integer.parseInt(stub.getStringState(account2Key));

        // 执行转账操作
        if (account1Value < account) {
            return newErrorResponse("转账人余额不足");
        }
        account1Value -= account;
        account2Value += account;

        stub.putStringState(account1Key, String.valueOf(account1Value));
        stub.putStringState(account2Key, String.valueOf(account2Value));

        return newSuccessResponse("转账成功");
    }

    /**
     * 查询账户历史
     * @param stub
     * @param args
     * @return
     */
    private Response history(ChaincodeStub stub, List<String> args) {
        if (args.size() != 1) {
            return newErrorResponse("应传入1个参数");
        }
        String accountKey = args.get(0);

        // 校验参数不能为空
        if (StrUtil.isBlank(accountKey)) {
            return newErrorResponse("账户名为空");
        }

        QueryResultsIterator<KeyModification> keyModifications = stub.getHistoryForKey(accountKey);

        List<KeyModification> keyModificationList = new ArrayList<>();
        for (KeyModification keyModification : keyModifications) {
            keyModificationList.add(keyModification);
        }

        return newSuccessResponse("查询账户历史成功", JSON.toJSONBytes(keyModificationList));
    }

    public static void main(String[] args) {
        new ChaincodeExample().start(args);
    }
}