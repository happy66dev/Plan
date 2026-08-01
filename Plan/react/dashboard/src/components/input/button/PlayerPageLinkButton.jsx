import React from 'react';
import {FontAwesomeIcon as Fa} from "@fortawesome/react-fontawesome";
import {useTranslation} from "react-i18next";
import {Link} from "react-router";
import {faUser} from "@fortawesome/free-solid-svg-icons";
import {useAuth} from "../../../hooks/authenticationHook.tsx";
import {useEmbed} from "../../../embed/EmbedContext";

const PlayerPageLinkButton = ({uuid, className}) => {
    const {t} = useTranslation();
    // 喵~读取嵌入状态，嵌入玩家页不提供跳往其他玩家的入口喵~
    const {isEmbedMode} = useEmbed();
    const {authRequired, hasPermission, user} = useAuth();

    const canSeePlayer = hasPermission('access.player') || !authRequired
        || hasPermission('access.player.self') && uuid === user.playerUUID;
    // 喵~嵌入体验或无玩家权限时均不渲染其他玩家跳转按钮喵~
    if (isEmbedMode || !canSeePlayer) return <></>;

    return (
        <Link to={`/player/${uuid}`} className={`btn bg-players-online ${className || ''}`}>
            <Fa icon={faUser}/> {t('html.label.playerPage')}
        </Link>
    )
};

export default PlayerPageLinkButton