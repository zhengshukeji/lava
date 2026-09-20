import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'Lava',
  description: '面向 Java 25 的模块化基础设施工具库',
  cleanUrls: true,
  lastUpdated: true,
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      {
        text: '基础模块',
        items: [
          { text: 'lava-bom', link: '/lava-bom/' },
          { text: 'lava-core', link: '/lava-core/' },
          { text: 'lava-json', link: '/lava-json/' },
          { text: 'lava-http', link: '/lava-http/' },
          { text: 'lava-crypto', link: '/lava-crypto/' },
          { text: 'lava-schedule', link: '/lava-schedule/' },
          { text: 'lava-mail', link: '/lava-mail/' }
        ]
      },
      {
        text: '支付模块',
        items: [
          { text: 'lava-pay-wechat', link: '/lava-pay-wechat/' },
          { text: 'lava-pay-alipay', link: '/lava-pay-alipay/' }
        ]
      },
      {
        text: '平台模块',
        items: [
          { text: 'lava-jiandaoyun', link: '/lava-jiandaoyun/' }
        ]
      }
    ],

    sidebar: {
      '/lava-bom/': [
        {
          text: 'lava-bom',
          items: [
            { text: '版本管理', link: '/lava-bom/' }
          ]
        }
      ],
      '/lava-core/': [
        {
          text: 'lava-core',
          items: [
            { text: '模块概览', link: '/lava-core/' }
          ]
        },
        {
          text: '基础能力',
          items: [
            { text: 'ID 生成', link: '/lava-core/id' },
            { text: '重试', link: '/lava-core/retry' },
            { text: 'IO 与数据量', link: '/lava-core/io' },
            { text: '时间与参数校验', link: '/lava-core/time-validation' }
          ]
        }
      ],
      '/lava-json/': [
        {
          text: 'lava-json',
          items: [
            { text: '模块概览', link: '/lava-json/' },
            { text: 'JSON 编解码', link: '/lava-json/codec' },
            { text: 'Mapper 配置', link: '/lava-json/configuration' }
          ]
        }
      ],
      '/lava-http/': [
        {
          text: 'lava-http',
          items: [
            { text: '模块概览', link: '/lava-http/' },
            { text: '请求与响应', link: '/lava-http/requests' },
            { text: '请求体与 URL', link: '/lava-http/body-url' },
            { text: '流式响应', link: '/lava-http/streaming' },
            { text: 'SSE', link: '/lava-http/sse' },
            { text: '生命周期、失败与安全', link: '/lava-http/lifecycle-errors' }
          ]
        }
      ],
      '/lava-crypto/': [
        {
          text: 'lava-crypto',
          items: [
            { text: '模块概览', link: '/lava-crypto/' },
            { text: '密码哈希', link: '/lava-crypto/password' },
            { text: 'HMAC、RSA 与 AES-GCM', link: '/lava-crypto/algorithms' },
            { text: '密钥与 PEM', link: '/lava-crypto/keys-pem' }
          ]
        }
      ],
      '/lava-schedule/': [
        {
          text: 'lava-schedule',
          items: [
            { text: '模块概览', link: '/lava-schedule/' },
            { text: '触发器与任务', link: '/lava-schedule/triggers-tasks' },
            { text: '并发、Misfire 与生命周期', link: '/lava-schedule/policies-lifecycle' }
          ]
        }
      ],
      '/lava-mail/': [
        {
          text: 'lava-mail',
          items: [
            { text: '模块概览', link: '/lava-mail/' },
            { text: '发送邮件', link: '/lava-mail/sending' },
            { text: 'OAuth 2', link: '/lava-mail/oauth2' },
            { text: '读取邮件', link: '/lava-mail/reading' },
            { text: '限制、失败与安全', link: '/lava-mail/limits-security' }
          ]
        }
      ],
      '/lava-pay-wechat/': [
        {
          text: 'lava-pay-wechat',
          items: [
            { text: '模块概览', link: '/lava-pay-wechat/' },
            { text: '快速开始', link: '/lava-pay-wechat/quick-start' }
          ]
        },
        {
          text: '支付能力',
          items: [
            { text: 'Native 支付', link: '/lava-pay-wechat/native-pay' },
            { text: '回调通知', link: '/lava-pay-wechat/notification' },
            { text: '查单与关单', link: '/lava-pay-wechat/transaction' },
            { text: '退款与退款查询', link: '/lava-pay-wechat/refund' },
            { text: '账单', link: '/lava-pay-wechat/bill' }
          ]
        },
        {
          text: '上线准备',
          items: [
            { text: '异常与安全', link: '/lava-pay-wechat/errors-security' }
          ]
        }
      ],
      '/lava-pay-alipay/': [
        {
          text: 'lava-pay-alipay',
          items: [
            { text: '模块概览', link: '/lava-pay-alipay/' },
            { text: '快速开始', link: '/lava-pay-alipay/quick-start' }
          ]
        },
        {
          text: '支付能力',
          items: [
            { text: '电脑网站支付', link: '/lava-pay-alipay/page-pay' },
            { text: '异步通知', link: '/lava-pay-alipay/notification' },
            { text: '查单与关单', link: '/lava-pay-alipay/transaction' },
            { text: '退款与退款查询', link: '/lava-pay-alipay/refund' },
            { text: '账单下载地址', link: '/lava-pay-alipay/bill' }
          ]
        },
        {
          text: '上线准备',
          items: [
            { text: '异常与安全', link: '/lava-pay-alipay/errors-security' }
          ]
        }
      ],
      '/lava-jiandaoyun/': [
        {
          text: 'lava-jiandaoyun',
          items: [
            { text: '模块概览', link: '/lava-jiandaoyun/' },
            { text: '快速开始', link: '/lava-jiandaoyun/quick-start' }
          ]
        }
      ]
    },

    search: {
      provider: 'local'
    },
    outline: {
      level: [2, 3],
      label: '本页目录'
    },
    docFooter: {
      prev: '上一篇',
      next: '下一篇'
    },
    lastUpdated: {
      text: '最后更新于'
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/zhengshuyuncom/lava' }
    ]
  }
})
