#!/usr/bin/python
import sys
from multiprocessing import Process
import numpy as np
from scipy import stats

VALID_STR = 'VALID:'

def parse(f):
    records = {} #[label] = record_table
    record_table = []
    current_record = ''
    for line in f:
        if line.startswith(">>>>>>>>>>"):
            current_record = line[11:-1]
            record_table = []
            records[current_record] = record_table
        elif line.startswith("<<<<<<<<<<"):
            if current_record in records:
                records[VALID_STR + current_record] = record_table
                del records[current_record]
            current_record = ''
            record_table = []
        else:
            args = line.split(',')
            if len(args) >= 3:
                level = int(args[2])
                time = long(args[1])
                record_table.append((time, level))
    return records

def refactor_time(times):
    baseline = float(min(times))
    factor = 3600*1000 #convert to hours
    return [float(elem - baseline)/factor for elem in times]

def drain_rate(record):
    x_values = []
    y_values = []
    for elem in record:
        x_values.append(elem[0])
        y_values.append(elem[1])
    x_min, x_max = min(x_values), max(x_values)
    y_min, y_max = min(y_values), max(y_values)
    return 3600*1000*float(y_max - y_min)/float(x_max - x_min)

def execution_time(record):
    x_values = []
    for elem in record:
        x_values.append(elem[0])

    x_values = refactor_time(x_values)
    return max(x_values)

def energy_use(record):
    y_values = []
    for elem in record:
        y_values.append(elem[1])

    return max(y_values) - min(y_values)


def plot(record, title='Title'):
    import matplotlib.pyplot as plt
    x_values = []
    y_values = []
    for elem in record:
        x_values.append(elem[0])
        y_values.append(elem[1])

    x_values = refactor_time(x_values)
    x_min, x_max = min(x_values), max(x_values)
    y_min, y_max = min(y_values), max(y_values)

    x = np.array(x_values)
    y = np.array(y_values)

    slope, intercept, r_value, p_value, std_err = stats.linregress(x,y)

    print "r-squared:", r_value**2

    plt.plot(x_values, y_values, 'bo', label='Original data')
    plt.axis([x_min, x_max, y_min, y_max])
    plt.xlabel('Time (Hours)')
    plt.ylabel('% Battery')
    plt.title(title)
    plt.legend()
    plt.show()

def plot_all(result):
    jobs = {x for x in result.keys() if len(result[x]) > 0}
    threads = [Process(target = plot, args=(result[job], job)) for job in jobs]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

def get_valid_record_keys(records):
    # returns a new record table pointing to valid record table entries
    return [key for key in records.keys() if key.startswith(VALID_STR)]

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print "Usage: python parser.py <log_file>"
        sys.exit(-1)

    result = {}
    for file_name in sys.argv[1:]:
        f = open(file_name)
        file_result = parse(f)
        result.update(file_result)
    drain_rates = {x: drain_rate(result[x]) for x in result.keys() if len(result[x]) > 1}
    times = {x: execution_time(result[x]) for x in result.keys() if len(result[x]) > 1}
    energy = {x: energy_use(result[x]) for x in result.keys() if len(result[x]) > 1}

    _drain_rates_valid = {x: drain_rate(result[x]) for x in get_valid_record_keys(result) if len(result[x]) > 1}
    _times_valid = {x: execution_time(result[x]) for x in get_valid_record_keys(result) if len(result[x]) > 1}