var summaryInfoApp = angular.module('summaryInfoApp', ['http-auth-interceptor', 'angucomplete-alt']);
summaryInfoApp.controller('summaryController', function ($scope, $http, $q) {
    $scope.query = {
        userId: ''
    };
    $scope.cacheRequestFn = function (str) {
        return {q: str, timestamp: new Date().getTime()};
    };

    $scope.remoteUrl = function () {
        return $scope.context.targetsUrl;
    };

    $scope.context = {
        targetIdName: 'userId',
        targetNameArr: ['email', 'id', 'name'],
        targetsUrl: G.baseUrl + '/api/meta/users',
        targetsName: 'users'
    };

    $scope.target = {
        id: null,
        name: ''
    };
    $scope.targets = {};
    $scope.clickTarget= function () {
        $('#targetSelector_value').css('width','250px');
    };
    $scope.getAllTargets = function () {
        var c = $scope.context;
        $http.get(c.targetsUrl).success(
            function (res) {
                if (res.length > 0) {
                    $.each(res, function (i, val) {
                        $scope.targets[val.id] = val;
                    });
                }
                if ($scope.target.id) {
                    if ($scope.targets[$scope.target.id])
                        $scope.target.name = $scope.target.id;
                    else {
                        $http.get("/api/auth/user?userId=" + $scope.target.id).success(
                            function (res) {
                                $scope.target.name = $scope.target.id;
                            }
                        );
                    }
                }
            }
        );
    };
    $scope.selectTarget = function (t) {
        if (t) {
            var toId = t.originalObject.name;
            if ($scope.target.id != toId) {
                $scope.$broadcast('angucomplete-alt:clearInput', 'targetSelector');
                var pairs = {};
                pairs[$scope.context.targetIdName] = toId;
                H.setData(pairs);
                messageNotify("切换用户:", "成功切换至用户: " + toId, null);
            }
        }
    };
    $scope.data = {
        current: '基本信息',
        links: ['基本信息', '权限', '操作日志'],
        hrefs: {
            '基本信息': '/portal/user',
            '操作日志': '/portal/user/log',
            '权限': '/portal/user/user-access'
        }
    };

    $scope.isCurrentInfoPage = function (link) {
        return $scope.data.current == link ? 'current' : '';
    };

    $scope.generateInfoLink = function (link) {
        var b = $scope.data.hrefs[link] + "#?env=" + G.env;
        if ($scope.query.userId) {
            b += '&userId=' + $scope.query.userId;
        }
        return b;
    };

    $scope.hashChanged = function (hashData) { $scope.resource = H.resource;
        if (hashData.env) {
            $scope.env = hashData.env;
        }
        var n = $scope.context.targetIdName;
        if (hashData[n]) {
            $scope.target.id = hashData[n];
            $scope.getAllTargets();
        }
        if (hashData.userId) {
            $scope.query.userId = hashData.userId;
        }
        $scope.target = {};
        if (hashData.userId) {
            $scope.target.name = hashData.userId;
        } else {
            $scope.target.name = 'Me';
        }
    };
    H.addListener("summaryInfoApp", $scope, $scope.hashChanged);
    function messageNotify(title, message, url) {
        var notify = $.notify({
            icon: '',
            title: title,
            message: message,
            url: url,
            target: '_self'
        }, {
            type: 'success',
            allow_dismiss: true,
            newest_on_top: true,
            placement: {
                from: 'top',
                align: 'center'
            },
            offset: {
                x: 0,
                y: 0
            },
            animate: {
                enter: 'animated fadeInDown',
                exit: 'animated fadeOutUp'
            },
            delay: 1000,
            spacing: 5,
            z_index: 1031,
            mouse_over: 'pause'
        });
    }
});
angular.bootstrap(document.getElementById("summary-area"), ['summaryInfoApp']);


//InfoLinksComponent: info links
var headerInfoApp = angular.module('headerInfoApp', ["angucomplete-alt"]);
headerInfoApp.controller('headerInfoController', function ($scope, $http) {
    $scope.query = {};
    $scope.generateLink = function (a) {
        var link = '';
        switch (a) {
            case 'home': {
                link = "/portal/user-home#?env=" + G.env;
                break;
            }
            case 'basic':
            {

                link = "/portal/user#?env=" + G.env;
                break;
            }
            case 'access':
            {
                link = "/portal/user/user-access#?env=" + G.env;
                break;
            }
            case 'log':
            {
                link = "/portal/user/log#?env=" + G.env;

                break;
            }
            case 'policy':
            {
                link = "/portal/user/user-policy#?env=" + G.env;
                break;
            }

            default:
                break;
        }
        if ($scope.query.userId) {
            link += '&userId=' + $scope.query.userId;
        }
        return link;
    }
    $scope.hashChanged = function (hashData) { $scope.resource = H.resource;
        if (hashData.env) {
            $scope.env = hashData.env;
        }
        if (hashData.userId) {
            $scope.query.userId = hashData.userId;
        }
    };
    H.addListener("infoLinksApp", $scope, $scope.hashChanged);
});
angular.bootstrap(document.getElementById("header-area"), ['headerInfoApp']);


var trafficsQueryApp = angular.module('trafficsQueryApp', ["angucomplete-alt", "http-auth-interceptor"]);
trafficsQueryApp.controller('trafficsQueryController', function ($scope, $http, $q) {
    $scope.query = {
        "trafficid": "",
        "trafficname": "",
        states:{},
        target:{}
    };
    $scope.data = {
        trafficArr: [],
        vsArr: [],
        vsArr: [],
        statusArr: [],
        trafficForArr:[]
    };

    //Load cache Area
    $scope.cacheRequestFn = function (str) {
        return {q: str, timestamp: new Date().getTime()};
    };

    $scope.remoteGroupsUrl = function () {
        return G.baseUrl + "/api/meta/groups";
    };
    $scope.remoteTrafficsUrl = function () {
        return G.baseUrl + "/api/meta/policies";
    };
    $scope.remoteVsesUrl = function () {
        return G.baseUrl + "/api/meta/vses";
    };

    //Load Query Data Area
    $scope.dataLoaded = false;
    $scope.loadData = function (hashData) {
        if ($scope.env == hashData.env && $scope.dataLoaded) return;
        $scope.dataLoaded = true;
        $scope.env = hashData.env;

        // refresh the query data object
        $scope.data = {
            trafficArr: [],
            vsArr: [],
            groupArr: [],
            tagArr: []
        };

        $http.get(G.baseUrl + "/api/tags?type=policy").success(function (res) {
            $scope.data.tagArr = _.map(res['tags'], function (val) {
                return {name: val};
            });
        });
        $scope.data.statusArr = ["已激活", "有变更", "未激活"];
        $scope.data.trafficForArr=['.NET转Java','大服务拆分','多版本测试','VM转容器','其它'];
    };
    // Input changed event
    $scope.trafficIdInputChanged = function (o) {
        $scope.query.trafficid = o;
    };
    $scope.vsIdInputChanged = function (o) {
        $scope.query.vsid = o;
    };
    $scope.groupIdInputChanged = function (o) {
        $scope.query.groupid = o;
    };
    // Select input field
    $scope.selectTrafficId = function (o) {
        if (o) {
            $scope.query.trafficid = o.originalObject.id;
        }
    };
    $scope.selectVsId = function (o) {
        if (o) {
            $scope.query.vsid = o.originalObject.id;
        } else {
        }
    };
    $scope.selectGroupId = function (o) {
        if (o) {
            $scope.query.groupid = o.originalObject.id;
        } else {
        }
    };

    $scope.toggleStatus = function (status) {
        if ($scope.query.states[status]) {
            delete $scope.query.states[status];
        } else {
            $scope.query.states[status] = status;
        }
    };
    $scope.isSelectedStatus = function (status) {
        if ($scope.query.states[status]) {
            return "label-info";
        }
    };
    $scope.statusClear = function () {
        $scope.query.states = [];
    };

    $scope.toggleTarget= function (target) {
        if ($scope.query.target[target]) {
            delete $scope.query.target[target];
        } else {
            $scope.query.target[target] = target;
        }
    };
    $scope.isSelectedTarget = function (target) {
        if ($scope.query.target[target]) {
            return "label-info";
        }
    };
    $scope.targetClear = function () {
        $scope.query.target = {};
    };

    $scope.showClear = function (type) {
        if (type == "status") {
            return _.keys($scope.query.states).length > 0 ? "link-show" : "link-hide";
        }
        if (type == "target") {
            return _.keys($scope.query.target).length > 0 ? "link-show" : "link-hide";
        }
    };
    $scope.clearQuery = function () {
        $scope.query.states = {};
        $scope.query.trafficid = "";
        $scope.query.trafficname = "";
        $scope.query.vsid = "";
        $scope.query.groupid = "";
        $scope.query.target={};

        $scope.setInputsDisplay();
    };
    $scope.executeQuery = function () {
        var hashData = {};
        hashData.trafficId = $scope.query.trafficid || "";
        hashData.trafficName = $scope.query.trafficname || "";
        hashData.vsId = $scope.query.vsid || "";
        hashData.groupId = $scope.query.groupid || "";
        hashData.policyTags = _.values($scope.query.tags);
        hashData.policyStatus = _.values($scope.query.states);
        hashData.policyTarget= _.values($scope.query.target);
        hashData.timeStamp = new Date().getTime();
        H.setData(hashData);
    };
    //Init input field while hashChanged
    $scope.setInputsDisplay = function () {
        $('#trafficIdSelector_value').val($scope.query.trafficid);
        $('#vsIdSelector_value').val($scope.query.vsid);
        $('#groupIdSelector_value').val($scope.query.groupid);
    };
    $scope.applyHashData = function (hashData) {
        $scope.query.trafficname = hashData.trafficName;
        $scope.query.vsid = hashData.vsId;
        $scope.query.groupid = hashData.groupId;
        $scope.query.tags = {};

        if (hashData.policyTags) {
            $.each(hashData.policyTags.split(","), function (i, val) {
                $scope.query.tags[val] = val;
            })
        }
        $scope.query.states = {};
        if (hashData.policyStatus) {
            $.each(hashData.policyStatus.split(","), function (i, val) {
                $scope.query.states[val] = val;
            })
        }
        $scope.query.target={};
        if (hashData.policyTarget) {
            $.each(hashData.policyTarget.split(","), function (i, val) {
                $scope.query.target[val] = val;
            })
        }
    };
    $scope.hashChanged = function (hashData) { $scope.resource = H.resource;
        $scope.loadData(hashData);
        $scope.applyHashData(hashData);
        $scope.setInputsDisplay();
    };
    H.addListener("trafficsQueryApp", $scope, $scope.hashChanged);
});
angular.bootstrap(document.getElementById("traffics-query-area"), ['trafficsQueryApp']);

var selfInfoApp = angular.module('selfInfoApp', ["angucomplete-alt", "http-auth-interceptor"]);
selfInfoApp.controller('selfInfoController', function ($scope, $http, $q) {
    $scope.ownGroup = false;
    $scope.userInfo = {};
    $scope.apps = {};
    $scope.query = {
        userId: '',
        loginUserId: ''
    };

    $scope.model={
        vses:{},
        groups:{},
        apps:{},
        policies:{}
    };
    $scope.userInfo = {};
    $scope.loadData = function (hashData) {
        var name = '';
        var url = '';
        if ($scope.query.userId) {
            url = G.baseUrl + '/api/auth/user?userName=' + $scope.query.userId;
        } else {
            url = '/api/auth/current/user';
        }
        var request = {
            method: 'GET',
            url: url
        };
        $http(request).success(
            function (res) {
                $scope.queryResult = res;
                $scope.userInfo = {};
                if (res) {
                    if (res['name']) {
                        name = res['name'];
                    } else {
                        name = res['user-name'];
                    }
                }
            }
        ).then(
            function () {
                if ($scope.queryResult.code) {
                    exceptionNotify("出错了!!", "加载当前用户信息失败了， 失败原因" + $scope.queryResult.message, null);
                    return;
                } else {
                    setTimeout(
                        function () {
                            $('.alert-danger').remove();
                        },
                        1000
                    );
                }
                $scope.userName = name;
                $scope.loadMeta(hashData,name);
            }
        );
    };
    $scope.loadMeta = function (hashData, name) {
        var queryString = $scope.getTrafficsQueryString(hashData);
        queryString = SpellTagsQueryString(queryString,['owner_'+name])

        var request={
            method: 'GET',
            url: queryString
        };
        var groupsRequest = {
            method: 'GET',
            url: G.baseUrl + '/api/groups?type=extended&groupType=all'
        };
        var vsesRequest = {
            method: 'GET',
            url: G.baseUrl + '/api/vses?type=extended'
        };
        var appsRequest={
            method: 'GET',
            url: G.baseUrl+'/api/apps'
        };
        $q.all(
            [
                $http(request).success(
                    function (response) {
                        if (response['traffic-policies']) {
                            $scope.model.policies = _.indexBy(response['traffic-policies'], function (item) {
                                return item['id'];
                            });
                        }
                    }
                ),
                $http(appsRequest).success(
                    function (response) {
                        if (response['apps']) {
                            $scope.model.apps = _.indexBy(response['apps'], function (item) {
                                return item['app-id'];
                            });
                        }
                    }
                ),
                $http(vsesRequest).success(
                    function (response) {
                        var vses = response['virtual-servers'];
                        if (vses) {
                            $scope.model.vses = _.indexBy(vses, 'id');
                        }
                    }
                ),

                $http(groupsRequest).success(
                    function (response) {
                        var groups = response['groups'];
                        if (groups) {
                            $scope.model.groups = _.indexBy(groups, 'id');
                        }
                    }
                )
            ]
        ).then(
            function () {
                var statusitems = _.countBy($scope.model.policies, function (r) {
                    var v = _.find(r.properties, function (t) {
                        return t.name=='status';
                    });
                    if(v) return v.value;
                    return '-';
                });
                $('.summary').html('共有 '+ _.keys($scope.model.policies).length+' 个分流策略, 其中 已激活 '+(statusitems['activated'] | 0)+' 个, 有变更 '+(statusitems['toBeActivated'] | 0)+' 个, 未激活 '+(statusitems['deactivated'] | 0)+' 个');
                var mappings = _.map(_.keys($scope.model.policies), function (key) {
                    var policy = $scope.model.policies[key];
                    return{
                        key: key,
                        value:$scope.getGraphicData(policy)
                    };
                });

                // paint the areas
                var array= _.pluck(mappings,'value');
                var t=[];
                $.each(array, function (i,c) {
                    t= t.concat(c);
                });

                TrafficPolicyGraphics.draw(t, $('#diagram-canvas'));
                $('#diagram-canvas').hideLoading();
            }
        )
    };

    $scope.getTrafficsQueryString = function (hashData) {
        var queryString = G.baseUrl + "/api/policies?type=EXTENDED";
        // If there is only one env hashdata in the url
        if (_.keys(hashData).length == 1 && hashData.env) {
        } else {
            queryString = SpellQueryString(queryString, hashData.trafficId, "policyId");
            queryString = SpellQueryString(queryString, hashData.groupName, "policyName");
            queryString = SpellQueryString(queryString, hashData.vsId, "vsId");
            queryString = SpellQueryString(queryString, hashData.groupId, "groupId");
            queryString = SpellPropertyQueryString(queryString, {"status": hashData.policyStatus});
            queryString = SpellPropertyQueryString(queryString, {"target": hashData.policyTarget});
            var tags = [];
            if (hashData.policyTags == "" || hashData.policyTags == undefined) tags = [];
            else tags = hashData.policyTags.split(',');
            if (tags.length > 0) {
                queryString = SpellTagsQueryString(queryString, tags);
            }
        }
        return queryString;
    };
    function SpellQueryString(queryString, property, tag) {
        if (property) {
            var v = property.split(":")[0];
            if (queryString.endsWith('?')) {
                queryString += (tag + "=");
            }
            else {
                queryString += ("&" + tag + "=");
            }
            return queryString + v;
        }
        else return queryString;
    }

    function SpellTagsQueryString(queryString, tags) {
        if (tags.length == 0 || tags[0] == "") return queryString;

        var query = "anyTag=";
        $.each(tags, function (i, tag) {
            query += tag + ",";
        });

        // trim last ','
        var lastSymIndex = query.lastIndexOf(',');
        query = query.substr(0, lastSymIndex);

        if (queryString.endsWith('?')) {
            queryString += query;
        }
        else {
            queryString += ("&" + query);
        }
        return queryString;
    }

    function SpellPropertyQueryString(queryString, property) {
        if (property != undefined && _.keys(property).length > 0) {
            var query = "anyProp=";
            var keys = _.keys(property);

            $.each(keys, function (i, key) {
                if (property[key]) {
                    var v = property[key].split(',');
                    $.each(v, function (index, unit) {
                        query += key + ":" + getRealpValues(unit) + ",";
                    });
                }
            });
            // trim last ','
            var lastSymIndex = query.lastIndexOf(',');
            if (lastSymIndex != -1) {
                query = query.substr(0, lastSymIndex);

                if (queryString.endsWith('?')) {
                    queryString += query;
                }
                else {
                    queryString += ("&" + query);
                }
            }
            return queryString;
        }
        else return queryString;
    }


    $scope.getGraphicData = function (policy) {
        var policy_clone = $.extend({}, true, policy);
        var domains = [];
        var idc = '-';
        var pollicyname='-';
        var diffs = $scope.diffVses;
        policy_clone['policy-virtual-servers'] = _.reject(policy_clone['policy-virtual-servers'], function (item) {
            var v = _.find(diffs, function (r) {
                return r.vsId == item['virtual-server'].id;
            });
            if (v == undefined) return false;
            return true;
        });
        var vses = policy_clone['policy-virtual-servers'];

        var result =  _.map(vses, function (item) {
                var vsId = item['virtual-server'].id;
                var path = item.path.trim();
                if (path == '/' || path == '~* ^/') path = path;
                else {
                    // standard paths?
                    if (path.startsWith('~* ^/') && path.endsWith('($|/|\\?)')) {
                        path = path.substring(5, path.length - 8);
                    } else {
                        if (!path.endsWith('($|/|\\?)')) {
                            path = path.substring(5, path.length);
                        }
                    }
                }

                var vs = $scope.model.vses[vsId];
                if (vs) {
                    var p = _.find(vs.properties, function (r) {
                        return r.name == 'idc';
                    });
                    if (p) idc = p.value;

                    var protocol = vs.ssl ? 'https://' : 'http://';
                    domains = _.map(vs.domains, function (t) {
                        return protocol + t.name + '/' + path;
                    });
                }
                pollicyname = policy_clone.name;

                var totalWeight = _.reduce(_.pluck(policy_clone.controls, 'weight'), function (memo, num) {
                    return memo + num;
                });
                var groups_temp = _.map(policy_clone.controls, function (s) {
                    var groupidc='-';
                    var a = _.find($scope.model.groups[s.group.id].properties, function (t) {
                        return t.name=='idc';
                    });
                    if(a) groupidc= a.value;

                    var groupdeployidc = '-';
                    var c = _.find($scope.model.groups[s.group.id].properties, function (t) {
                        return t.name == 'idc_code';
                    });
                    if (c) groupdeployidc = c.value;

                    var appid= $scope.model.groups[s.group.id]['app-id'];
                    return {
                        name: $scope.model.groups[s.group.id].name,
                        id: s.group.id,
                        weight: T.getPercent(s.weight, totalWeight),
                        idc:groupidc,
                        'app-id':appid,
                        idc_code: G['idcs'][groupdeployidc] || groupdeployidc,
                        'app-name':$scope.model.apps[appid]?$scope.model.apps[appid]['chinese-name']:'-'
                    };
                });

                return {
                    vs: {
                        id: vsId,
                        domains:domains,
                        idc:idc
                    },
                    policy: {
                        id: policy.id,
                        name: pollicyname
                    },
                    groups:groups_temp
                }
            }
        );
        return result;
    };

    $scope.hashChanged = function (hashData) { $scope.resource = H.resource;
        $('#diagram-canvas').html('');

        $scope.model.policies={};
        $('#diagram-canvas').showLoading();
        $scope.userInfo = {};
        if (hashData.env) {
            $scope.env = hashData.env;
        }
        if (hashData.userId) {
            $scope.query.userId = hashData.userId;
        } else {
            $scope.query.userId = '';
        }
        $scope.loadData(hashData);
    };
    function getGroupStatus(val) {
        switch (val) {
            case 'activated':
                return '已激活';
            case 'toBeActivated':
                return '有变更';
            case 'deactivated':
                return '未激活';
            default:
                return val;
        }
    }
    function getRealpValues(val) {
        switch (val) {
            case '已激活':
                return 'activated';
            case '有变更':
                return 'toBeActivated';
            case '未激活':
                return 'deactivated';
            case '健康拉出':
                return 'unhealthy';
            case '发布拉出':
                return 'unhealthy';
            case 'Member拉出':
                return 'unhealthy';
            case 'Server拉出':
                return 'unhealthy';
            case 'Broken':
                return 'broken';
            default:
                return val;
        }
    }
    H.addListener("selfInfoApp", $scope, $scope.hashChanged);
    function exceptionNotify(title, message, url) {
        var notify = $.notify({
            icon: 'fa fa-exclamation-triangle',
            title: title,
            message: message,
            url: url,
            target: '_self'
        }, {
            type: 'danger',
            allow_dismiss: true,
            newest_on_top: true,
            placement: {
                from: 'top',
                align: 'center'
            },
            offset: {
                x: 0,
                y: 0
            },
            animate: {
                enter: 'animated fadeInDown',
                exit: 'animated fadeOutUp'
            },
            delay: 60000 * 24,
            spacing: 5,
            z_index: 1031,
            mouse_over: 'pause'
        });
    }
});
angular.bootstrap(document.getElementById("self-info-area"), ['selfInfoApp']);

(function (slb, $, undefined) {
    slb.test = function () {
        $('.diagram-body').click(function (e) {
            alert(111);
            e.preventDefault();
            var link = $(this).attr('tag');
            window.location.href=link;
        });
    };
})(window.slb = window.slb || {}, $, undefined);
