#!/usr/bin/python
import sys
import matplotlib.pyplot as plt
from multiprocessing import Process


def parse(f):
    records = {} #[label] = record_table
    record_table = []
    current_record = None
    for line in f:
        if line.startswith(">>>>>>>>>>"):
            current_record = line[11:-1]
            record_table = []
            records[current_record] = record_table
        elif line.startswith("<<<<<<<<<<"):
            current_record = None
            record_table = {}
        else:
            args = line.split(',')
            if len(args) == 3:
                level = int(args[2])
                time = long(args[1])
                record_table.append((time, level))
    return records

def refactor_time(times):
    baseline = float(min(times))
    factor = 3600*10.0E3 #convert to hours
    return [float(elem - baseline)/factor for elem in times]


def plot(record, title='Title'):
    x_values = []
    y_values = []
    for elem in record:
        x_values.append(elem[0])
        y_values.append(elem[1])

    x_values = refactor_time(x_values)
    x_min, x_max = min(x_values), max(x_values)
    y_min, y_max = min(y_values), max(y_values)

    plt.plot(x_values, y_values, 'b-')
    plt.axis([x_min, x_max, y_min, y_max])
    plt.title(title)
    plt.show()

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print "Usage: python parser.py <log_file>"
        sys.exit(-1)
    f = open(sys.argv[1])
    result = parse(f)
    jobs = {x for x in result.keys() if len(result[x]) > 0}
    threads = [Process(target = plot, args=(result[job], job)) for job in jobs]
    for t in threads:
        t.start()
    for t in threads:
        t.join()


