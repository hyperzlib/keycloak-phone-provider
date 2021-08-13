<?php
function getCsv($line){
    $items = explode("\t", $line);
    foreach($items as $key => $item){
        $items[$key] = str_replace('"', '', $item);
    }
    return $items;
}

function parseCsv($csvContent){
    $csvContent = str_replace("\r\n", "\n", $csvContent);
    $lines = explode("\n", $csvContent);
    if(count($lines) === 0) return [];
    $csv = [];
    foreach($lines as $line){
        if(!empty(trim($line))){
            $csv[] = getCsv($line);
        }
    }
    $headerLen = count($csv[0]);
    array_walk($csv, function(&$a) use ($csv, $headerLen) {
        if(count($a) === $headerLen)
            $a = array_combine($csv[0], $a);
    });
    array_shift($csv); # remove column header
    return $csv;
}