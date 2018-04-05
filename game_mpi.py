from mpi4py import MPI
import numpy

comm = MPI.COMM_WORLD
size = comm.Get_size()
rank = comm.Get_rank()
stat = MPI.Status()


import numpy
import sys
from matplotlib import pyplot as plt


#prob = float(sys.argv[1])
#COLS = int(sys.argv[2])
#ROWS = int(sys.argv[3])
#generations = int(sys.argv[4])
prob = 0.7
COLS = 200
ROWS = 198
generations = 100

N=numpy.random.binomial(1,prob,size=(ROWS+2)*COLS)
M=numpy.reshape(N,(ROWS+2,COLS))

M[0,:] = 0
M[ROWS+1,:] = 0
M[:,0] = 0
M[:,COLS-1] = 0

initM = numpy.copy(M)
print(initM)
#print("First Generation")
##plt.imshow(initM, interpolation='nearest')
##plt.show()


if size > ROWS:
        print("Not enough ROWS")
        exit()
subROWS=ROWS//size+2


# Function definitions 
#@profile
def msgUp(subGrid):
# Sends and Recvs rows with Rank+1
    comm.send(subGrid[subROWS-2,:],dest=rank+1)
    subGrid[subROWS-1,:]=comm.recv(source=rank+1)
    return 0

def msgDn(subGrid):
# Sends and Recvs rows with Rank-1
    comm.send(subGrid[1,:],dest=rank-1)
    subGrid[0,:] = comm.recv(source=rank-1)
    return 0



def computeGridPoints(subGrid):
        generation = generation + 1
        print ("Generation = ",generation)
        intermediateM = numpy.copy(subGrid)
        for subROW in range(1,subROWS-1):
            for COLelem in range(1,COLS-1):
                        sum = ( subGrid[subROW-1,COLelem-1]+subGrid[subROW-1,COLelem]+subGrid[subROW-1,COLelem+1]
                            +subGrid[subROW,COLelem-1]+subGrid[subROW,COLelem+1]
                            +subGrid[subROW+1,COLelem-1]+subGrid[subROW+1,COLelem]+subGrid[subROW+1,COLelem+1] )
                        print(subROW," ",COLelem," ",sum)
                        if subGrid[subROW,COLelem] == 1:
                                if sum < 2:
                                        intermediateM[subROW,COLelem] = 0
                                elif sum > 3:
                                        intermediateM[subROW,COLelem] = 0
                                else:
                                        intermediateM[subROW,COLelem] = 1
                        if subGrid[subROW,COLelem] == 0:
                                if sum == 3:
                                        intermediateM[subROW,COLelem] = 1
                                else:
                                        intermediateM[subROW,COLelem] = 0
        subGrid = numpy.copy(intermediateM)
        if numpy.sum(subGrid) == 0:
                print("Extinction Occurs at generation = ",generation)
                plt.imshow(subGrid, interpolation='nearest')
                plt.show()
                break
        print(" ")
        print(subGrid)

        
def compareGridPoints(oldGrid,newGrid):
    threshold = 1
    OG=numpy.asarray(oldGrid)
    NG=numpy.asarray(newGrid)
    if OG.size != NG.size :
        print 'Grid sizes do not match'
        return 1
    for i in range(OG.size):
        if threshold < numpy.any(numpy.abs(numpy.subtract(OG,NG))):
            print 'Change detected at iteration: %d'%(i)
            return newGrid 
        else:
            print 'No Change detected at iteration: %d'%(i)
            return oldGrid

# The main body of the algorithm
#compute new grid and pass rows to neighbors
oldGrid=comm.gather(subGrid[1:subROWS-1,:],root=0)
for i in xrange(1,100):
    if i%10 == 0:
        newGrid=comm.gather(subGrid[1:subROWS-1,:],root=0)
        if 0 == rank:
            oldGrid=compareGridPoints(oldGrid,newGrid)
    computeGridPoints(subGrid)
    #exhange edge rows for next interation	
    if rank == 0:
        msgUp(subGrid)
    elif rank == size-1:
        msgDn(subGrid)
    else:
        msgUp(subGrid)
        msgDn(subGrid)


newGrid=comm.gather(subGrid[1:subROWS-1,:],root=0)

if rank == 0:
    result= numpy.vstack(newGrid)
    #print numpy.vstack(initGrid)
    print result[:]
    #print len(result)

