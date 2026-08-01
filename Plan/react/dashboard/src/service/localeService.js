import i18next from "i18next";
import I18NextChainedBackend from "i18next-chained-backend";
import I18NextLocalStorageBackend from "i18next-localstorage-backend";
import I18NextHttpBackend from 'i18next-http-backend';
import {initReactI18next, useTranslation} from 'react-i18next';
import {fetchAvailableLocales} from "./metadataService";
import {baseAddress, staticSite} from "./backendConfiguration";
import {isNumber} from "../util/isNumber.js";
import {isPlanEmbedCandidate} from "../embed/EmbedContext";
import {useMemo} from "react";

/**
 * A locale system for localizing the website.
 */
export const localeService = {
    /**
     * @function
     * Localizes an element.
     * @param {Element} element Element to localize
     * @param {Object} [options] Options
     */
    localize: {},

    /**
     * The current default language reported by the server.
     * @type {string}
     * @readonly
     */
    defaultLanguage: "",

    /**
     * The current available languages reported by the server.
     * @type {Object.<string, string>}
     * @readonly
     */
    availableLanguages: {},
    clientLocale: "",

    /**
     * Initializes the locale system. Gets the default & available languages from `/v1/locale`, and initializes i18next.
     */
    init: async function () {
        try {
            const {data} = await fetchAvailableLocales();
            if (data !== undefined) {
                this.defaultLanguage = data.defaultLanguage;
                this.availableLanguages = data.languages;
                this.languageVersions = data.languageVersions;
            } else {
                this.defaultLanguage = 'en'
                this.availableLanguages = {};
                this.languageVersions = [];
            }

            // 喵~嵌入体验始终强制中文，普通页面继续读取用户保存的语言偏好喵~
            this.clientLocale = isPlanEmbedCandidate() ? "CN" : window.localStorage.getItem("locale");
            // 喵~普通页面未保存语言时回退为服务器默认语言喵~
            if (!this.clientLocale) {
                this.clientLocale = this.defaultLanguage;
            }
            // 喵~防御：嵌入模式必须存在中文语言包，否则明确失败而非悄悄显示其他语言喵~
            if (isPlanEmbedCandidate() && !(this.clientLocale in this.availableLanguages)) {
                throw Error("The embedded player page requires the CN locale!");
            }
            let loadPath = baseAddress + '/v1/locale/{{lng}}';
            if (staticSite) loadPath = baseAddress + '/locale/{{lng}}.json'
            await i18next
                .use(I18NextChainedBackend)
                .use(initReactI18next)
                .init({
                    debug: false,
                    lng: this.clientLocale,
                    fallbackLng: false,
                    supportedLngs: Object.keys(this.availableLanguages),
                    showSupportNotice: false,
                    backend: {
                        backends: [
                            I18NextLocalStorageBackend,
                            I18NextHttpBackend
                        ],
                        backendOptions: [{
                            expirationTime: 7 * 24 * 60 * 60 * 1000, // 7 days
                            versions: this.languageVersions
                        }, {
                            loadPath: loadPath
                        }]
                    },
                }, () => {/* No need to initialize anything */
                });
        } catch (e) {
            console.error(e);
        }
    },

    /**
     * Loads a locale and translates the page.
     *
     * @param {string} langCode The two-character code for the language to be loaded, e.g. EN
     * @throws Error if an invalid langCode is given
     * @see /v1/language endpoint for available language codes
     */
    loadLocale: async function (langCode) {
        // 喵~防御：嵌入体验锁定中文，拒绝语言选择器或脚本切换到其他语言喵~
        if (isPlanEmbedCandidate() && langCode !== "CN") {
            return;
        }
        if (i18next.language === langCode) {
            return;
        }
        if (!(langCode in this.availableLanguages)) {
            throw Error(`The locale ${langCode} isn't available!`);
        }

        window.localStorage.setItem("locale", langCode);
        await i18next.changeLanguage(langCode);
        this.clientLocale = langCode;
    },

    getLanguages: function () {
        let languages = Object.fromEntries(Object.entries(this.availableLanguages)
            .sort((a, b) => a[0].localeCompare(b[0])));
        if ('CUSTOM' in languages) {
            // Move "Custom" to first in list
            delete languages["CUSTOM"]
            languages = Object.assign({"CUSTOM": "Custom"}, languages);
        }

        return Object.entries(languages)
            .map(entry => {
                return {name: entry[0], displayName: entry[1]}
            });
    },

    getIntlFriendlyLocale: () => {
        if (localeService.clientLocale === 'CUSTOM') return 'en';
        return localeService.clientLocale === 'CN' ? 'zh-cn' : localeService.clientLocale.toLocaleLowerCase().replace('_', '-')
    },

    localizePing: (value) => {
        if (isNumber(value)) {
            return new Intl.DurationFormat(localeService.getIntlFriendlyLocale(), {style: 'narrow'})
                .format({milliseconds: 1})
                .replace('1', value);
        }
        return value;
    }
}

const generateGeolocationMap = () => {
    const regions = new Intl.DisplayNames(['en'], {type: 'region'});
    const map = {}
    for (let i = 0; i < 26; i++) {
        for (let j = 0; j < 26; j++) {
            let code = String.fromCharCode(97 + i) + String.fromCharCode(97 + j)
            const result = regions.of(`${code}`);
            map[result] = code;
        }
    }
    return map;
}
export const reverseRegionLookupMap = generateGeolocationMap();

export const useI18nFriendlyLanguage = () => {
    const {i18n} = useTranslation();

    return useMemo(() => {
        if (i18n.language === 'CUSTOM') return 'en';
        return i18n.language === 'CN' ? 'zh-cn' : i18n.language.toLocaleLowerCase().replace('_', '-')
    });
}