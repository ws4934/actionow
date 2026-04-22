package com.actionow.ai.pricing;

/**
 * 积分计算异常
 * 当 Groovy 定价脚本执行失败时抛出，强制调用方处理（fail-closed）
 *
 * @author Actionow
 */
public class CreditCalculationException extends RuntimeException {

    public CreditCalculationException(String message) {
        super(message);
    }

    public CreditCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
