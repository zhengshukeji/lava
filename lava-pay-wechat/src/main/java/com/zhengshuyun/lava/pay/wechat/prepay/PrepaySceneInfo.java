/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhengshuyun.lava.pay.wechat.prepay;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 下单的支付场景信息。
 *
 * <p>Native 和 JSAPI 下单可选；H5 下单必填，且必须包含 {@link H5Info}。</p>
 *
 * @param payerClientIp 用户终端 IP
 * @param deviceId 商户端设备号
 * @param storeInfo 商户门店信息
 * @param h5Info H5 场景信息，仅 H5 下单使用
 */
public record PrepaySceneInfo(
        @JsonProperty("payer_client_ip") String payerClientIp,
        @JsonProperty("device_id") @Nullable String deviceId,
        @JsonProperty("store_info") @Nullable StoreInfo storeInfo,
        @JsonProperty("h5_info") @Nullable H5Info h5Info) {

    /**
     * 校验用户终端 IP 和可选设备信息。
     */
    public PrepaySceneInfo {
        payerClientIp = WechatPayValidationUtils.requireIpAddress(
                payerClientIp, "payerClientIp");
        if (deviceId != null) {
            ValidationUtils.requireNotBlank(deviceId, "deviceId must not be blank");
        }
    }

    /**
     * 创建支付场景信息构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 支付场景信息构建器。
     */
    public static final class Builder {
        /** 构建期用户终端 IP。 */
        private @Nullable String payerClientIp;
        /** 构建期商户端设备号。 */
        private @Nullable String deviceId;
        /** 构建期商户门店信息。 */
        private @Nullable StoreInfo storeInfo;
        /** 构建期 H5 场景信息。 */
        private @Nullable H5Info h5Info;

        /** 创建空支付场景构建器。 */
        private Builder() {
        }

        /**
         * 配置用户终端 IP。
         *
         * @param value 用户终端 IP
         * @return 当前构建器
         */
        public Builder payerClientIp(String value) {
            payerClientIp = value;
            return this;
        }

        /**
         * 配置商户端设备号。
         *
         * @param value 商户端设备号
         * @return 当前构建器
         */
        public Builder deviceId(String value) {
            deviceId = value;
            return this;
        }

        /**
         * 配置门店信息。
         *
         * @param value 门店信息
         * @return 当前构建器
         */
        public Builder storeInfo(StoreInfo value) {
            storeInfo = ValidationUtils.requireNonNull(value, "storeInfo must not be null");
            return this;
        }

        /**
         * 配置 H5 场景信息，H5 下单必填。
         *
         * @param value H5 场景信息
         * @return 当前构建器
         */
        public Builder h5Info(H5Info value) {
            h5Info = ValidationUtils.requireNonNull(value, "h5Info must not be null");
            return this;
        }

        /**
         * 校验并创建不可变场景信息。
         *
         * @return 支付场景信息
         */
        public PrepaySceneInfo build() {
            return new PrepaySceneInfo(ValidationUtils.requireNonNull(payerClientIp,
                    "payerClientIp is required"), deviceId, storeInfo, h5Info);
        }
    }

    /**
     * 下单场景中的商户门店信息。
     *
     * @param id 门店编号
     * @param name 门店名称
     * @param areaCode 地区编码
     * @param address 详细地址
     */
    public record StoreInfo(
            @JsonProperty("id") String id,
            @JsonProperty("name") @Nullable String name,
            @JsonProperty("area_code") @Nullable String areaCode,
            @JsonProperty("address") @Nullable String address
    ) {

        /**
         * 校验门店信息。
         */
        public StoreInfo {
            id = ValidationUtils.requireNotBlank(id, "storeInfo.id must not be blank");
            if (name != null) {
                ValidationUtils.requireNotBlank(name, "storeInfo.name must not be blank");
            }
            if (areaCode != null) {
                ValidationUtils.requireNotBlank(areaCode, "storeInfo.areaCode must not be blank");
            }
            if (address != null) {
                ValidationUtils.requireNotBlank(address, "storeInfo.address must not be blank");
            }
        }

        /**
         * 创建门店信息构建器。
         *
         * @return 新构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * 门店信息构建器。
         */
        public static final class Builder {
            /** 构建期门店编号。 */
            private @Nullable String id;
            /** 构建期门店名称。 */
            private @Nullable String name;
            /** 构建期地区编码。 */
            private @Nullable String areaCode;
            /** 构建期门店详细地址。 */
            private @Nullable String address;

            /** 创建空门店信息构建器。 */
            private Builder() {
            }

            /**
             * 配置门店编号。
             *
             * @param value 门店编号
             * @return 当前构建器
             */
            public Builder id(String value) {
                id = value;
                return this;
            }

            /**
             * 配置门店名称。
             *
             * @param value 门店名称
             * @return 当前构建器
             */
            public Builder name(String value) {
                name = value;
                return this;
            }

            /**
             * 配置地区编码。
             *
             * @param value 地区编码
             * @return 当前构建器
             */
            public Builder areaCode(String value) {
                areaCode = value;
                return this;
            }

            /**
             * 配置门店详细地址。
             *
             * @param value 门店详细地址
             * @return 当前构建器
             */
            public Builder address(String value) {
                address = value;
                return this;
            }

            /**
             * 校验并创建不可变门店信息。
             *
             * @return 门店信息
             */
            public StoreInfo build() {
                return new StoreInfo(
                        ValidationUtils.requireNonNull(
                                id,
                                "storeInfo.id is required"
                        ),
                        name,
                        areaCode,
                        address
                );
            }
        }
    }

    /**
     * H5 下单场景信息。
     *
     * @param type 场景类型，取 {@link H5Type} 中的常量
     * @param appName 应用名称
     * @param appUrl 网站地址
     * @param bundleId iOS 平台 BundleID
     * @param packageName Android 平台 PackageName
     */
    public record H5Info(
            @JsonProperty("type") String type,
            @JsonProperty("app_name") @Nullable String appName,
            @JsonProperty("app_url") @Nullable String appUrl,
            @JsonProperty("bundle_id") @Nullable String bundleId,
            @JsonProperty("package_name") @Nullable String packageName
    ) {

        /**
         * 校验场景类型。
         */
        public H5Info {
            type = ValidationUtils.requireNotBlank(type, "h5Info.type must not be blank");
        }

        /**
         * 创建只含场景类型的 H5 场景信息，适用于最常见的手机浏览器场景。
         *
         * @param type 场景类型，取 {@link H5Type} 中的常量
         * @return H5 场景信息
         */
        public static H5Info of(String type) {
            return new H5Info(type, null, null, null, null);
        }

        /**
         * 创建 H5 场景信息构建器。
         *
         * @return 新构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * H5 场景信息构建器。
         */
        public static final class Builder {
            /** 构建期场景类型。 */
            private @Nullable String type;
            /** 构建期应用名称。 */
            private @Nullable String appName;
            /** 构建期网站地址。 */
            private @Nullable String appUrl;
            /** 构建期 iOS BundleID。 */
            private @Nullable String bundleId;
            /** 构建期 Android PackageName。 */
            private @Nullable String packageName;

            /** 创建空 H5 场景信息构建器。 */
            private Builder() {
            }

            /**
             * 配置场景类型。
             *
             * @param value {@link H5Type} 中的场景类型常量
             * @return 当前构建器
             */
            public Builder type(String value) {
                type = value;
                return this;
            }

            /**
             * 配置应用名称。
             *
             * @param value 应用名称
             * @return 当前构建器
             */
            public Builder appName(String value) {
                appName = value;
                return this;
            }

            /**
             * 配置网站地址。
             *
             * @param value 网站地址
             * @return 当前构建器
             */
            public Builder appUrl(String value) {
                appUrl = value;
                return this;
            }

            /**
             * 配置 iOS 平台 BundleID。
             *
             * @param value BundleID
             * @return 当前构建器
             */
            public Builder bundleId(String value) {
                bundleId = value;
                return this;
            }

            /**
             * 配置 Android 平台 PackageName。
             *
             * @param value PackageName
             * @return 当前构建器
             */
            public Builder packageName(String value) {
                packageName = value;
                return this;
            }

            /**
             * 校验并创建不可变 H5 场景信息。
             *
             * @return H5 场景信息
             */
            public H5Info build() {
                return new H5Info(
                        ValidationUtils.requireNonNull(type, "h5Info.type is required"),
                        appName,
                        appUrl,
                        bundleId,
                        packageName
                );
            }
        }
    }
}
