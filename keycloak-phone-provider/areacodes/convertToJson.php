<?php
require_once "functions.php";

$csv = file_get_contents("isms_areacode.csv");

$data = parseCsv($csv);
$jsonData = [];

foreach($data as $one){
    if(isset($one['areacode']) && isset($one['code']) && isset($one['country']) && isset($one['countrycn'])){
        $jsonData[] = [
            'areaCode' => intval($one['areacode']),
            'countryCode' => $one['code'],
            'name' => [
                'en' => $one['country'],
                'zh-cn' => $one['countrycn']
            ]
        ];
    }
}

file_put_contents("areacode.json", json_encode($jsonData, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));